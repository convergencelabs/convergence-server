/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is part of the Convergence Server, which is released under
 * the terms of the GNU General Public License version 3 (GPLv3). A copy
 * of the GPLv3 should have been provided along with this file, typically
 * located in the "LICENSE" file, which is part of this source code package.
 * Alternatively, see <https://www.gnu.org/licenses/gpl-3.0.html> for the
 * full text of the GPLv3 license, if it was not provided.
 */

package com.convergencelabs.convergence.server.backend.services.domain

import com.convergencelabs.convergence.server.backend.datastore.domain.config.DomainConfigStore
import com.convergencelabs.convergence.server.backend.datastore.domain.group.UserGroupStore
import com.convergencelabs.convergence.server.backend.datastore.domain.jwt.JwtAuthKeyStore
import com.convergencelabs.convergence.server.backend.datastore.domain.session.SessionStore
import com.convergencelabs.convergence.server.backend.datastore.domain.user.{CreateNormalDomainUser, DomainUserStore, UpdateDomainUser}
import com.convergencelabs.convergence.server.backend.datastore.{DuplicateValueException, InvalidValueException}
import com.convergencelabs.convergence.server.model.DomainId
import com.convergencelabs.convergence.server.model.domain.jwt.JwtConstants
import com.convergencelabs.convergence.server.model.domain.session
import com.convergencelabs.convergence.server.model.domain.session.DomainSessionAndUserId
import com.convergencelabs.convergence.server.model.domain.user.{DomainUserId, DomainUserType}
import com.convergencelabs.convergence.server.model.server.domain.DomainAvailability
import com.convergencelabs.convergence.server.util.TryWithResource
import grizzled.slf4j.Logging
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.openssl.PEMParser
import org.jose4j.jwt.JwtClaims
import org.jose4j.jwt.consumer.{InvalidJwtException, JwtConsumerBuilder}

import java.io.StringReader
import java.security.spec.X509EncodedKeySpec
import java.security.{KeyFactory, PublicKey}
import java.time.{Duration, Instant}
import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success, Try}

object AuthenticationHandler {
  val AdminKeyId = "ConvergenceAdminKey"
  val AllowedClockSkew = 30
}

/**
 * The [AuthenticationHandler] class is a helper that assists the [DomainActor]
 * in authentic users connecting to the real time API.
 *
 * @param domainId          The id of the domain to authenticate users against.
 * @param domainConfigStore The store to obtain the configuration of the domain.
 * @param jwtKeyStore       The store containing JWT Auth Keys for the domain.
 * @param userStore         The user store for the domain.
 * @param userGroupStore    The user group store for the domain.
 * @param sessionStore      The session store for the domain where sessions should
 *                          be created.
 * @param ec                The execution context for asynchronous tasks.
 */
final class AuthenticationHandler(domainId: DomainId,
                                  domainConfigStore: DomainConfigStore,
                                  jwtKeyStore: JwtAuthKeyStore,
                                  userStore: DomainUserStore,
                                  userGroupStore: UserGroupStore,
                                  sessionStore: SessionStore,
                                  private[this] implicit val ec: ExecutionContext)
  extends Logging {

  private[this] val MaintenanceModeMessage =
    "Can not authenticate to a domain in maintenance mode."

  /**
   * Processes an authentication request for the associated domain.
   * @param request The auth request to process.
   * @return An Either whose left value contains an optional error message if
   *         authentication failed or a right containing information on the
   *         successful authentication.
   */
  def authenticate(request: AuthenticationCredentials, availability: DomainAvailability.Value): Either[Option[String], DomainActor.ConnectionSuccess] = {
    availability match {
      case DomainAvailability.Online =>
        request match {
          case message: PasswordAuthRequest =>
            authenticatePassword(message)
          case message: JwtAuthRequest =>
            authenticateJwt(message)
          case message: ReconnectTokenAuthRequest =>
            authenticateReconnectToken(message)
          case message: AnonymousAuthRequest =>
            authenticateAnonymous(message)
        }
      case DomainAvailability.Offline =>
        Left(Some("Can not authenticate to an offline domain."))
      case DomainAvailability.Maintenance =>
        request match {
          case message: JwtAuthRequest =>
            authenticateJwt(message, availability == DomainAvailability.Maintenance)
          case _ =>
            Left(Some(MaintenanceModeMessage))
        }
    }
  }

  //
  // Reconnect Auth
  //

  private[this] def authenticateReconnectToken(reconnectRequest: ReconnectTokenAuthRequest): Either[Option[String], DomainActor.ConnectionSuccess] = {
    userStore
      .validateAndRefreshReconnectToken(reconnectRequest.token, Duration.ofHours(24L))
      .flatMap {
        case Some(userId) =>
          authSuccess(userId, Some(reconnectRequest.token)).map(Right(_))
        case None =>
          Success(Left(None))
      }
      .recover {
        case cause =>
          error(s"$domainId: Unable to authenticate a user via reconnect token.", cause)
          Left(None)
      }
      .getOrElse(Left(None))
  }

  //
  // Anonymous Auth
  //

  private[this] def authenticateAnonymous(authRequest: AnonymousAuthRequest): Either[Option[String], DomainActor.ConnectionSuccess] = {
    val AnonymousAuthRequest(displayName) = authRequest
    debug(s"$domainId: Processing anonymous authentication request with display name: $displayName")
    domainConfigStore
      .isAnonymousAuthEnabled()
      .flatMap {
        case false =>
          debug(s"$domainId: Anonymous auth is disabled; returning AuthenticationFailure.")
          Success(Left(Some("anonymous authentication is disabled")))
        case true =>
          debug(s"$domainId: Anonymous auth is enabled; creating anonymous user.")
          userStore
            .createAnonymousDomainUser(displayName)
            .flatMap { username =>
              debug(s"$domainId: Anonymous user created: $username")
              val userId = DomainUserId(DomainUserType.Anonymous, username)
              authSuccess(userId, None)
            }
            .map(Right(_))
      }
      .recover {
        case cause: Throwable =>
          error(s"$domainId: Anonymous authentication error", cause)
          Left(None)
      }
      .getOrElse(Left(None))
  }

  //
  // Password Auth
  //
  private[this] def authenticatePassword(authRequest: PasswordAuthRequest): Either[Option[String], DomainActor.ConnectionSuccess] = {
    logger.debug(s"$domainId: Authenticating by username and password")
    userStore
      .validateNormalUserCredentials(authRequest.username, authRequest.password)
      .flatMap {
        case true =>
          val userId = DomainUserId(DomainUserType.Normal, authRequest.username)
          authSuccess(userId, None) map { response =>
            updateLastLogin(userId)
            Right(response)
          }
        case false =>
          Success(Left(None))
      }
      .recover {
        case cause: Throwable =>
          error(s"$domainId: Unable to authenticate a user", cause)
          Left(None)
      }
      .getOrElse(Left(None))
  }

  //
  // JWT Auth
  //
  private[this] def authenticateJwt(authRequest: JwtAuthRequest, maintenanceMode: Boolean = false): Either[Option[String], DomainActor.ConnectionSuccess] = {
    // This implements a two pass approach to be able to get the key id.
    val firstPassJwtConsumer = new JwtConsumerBuilder()
      .setSkipAllValidators()
      .setDisableRequireSignature()
      .setSkipSignatureVerification()
      .build()

    val jwtContext = firstPassJwtConsumer.process(authRequest.jwt)
    val objects = jwtContext.getJoseObjects
    val keyId = objects.get(0).getKeyIdHeaderValue

    getJWTPublicKey(keyId)
      .map { case (publicKey, admin) =>
        if (!maintenanceMode || admin ) {
          authenticateJwtWithPublicKey(authRequest, publicKey, admin)
            .map(Right(_))
            .recover {
              case cause: InvalidJwtException =>
                logger.debug(s"Invalid JWT: ${cause.getMessage}")
                Left(None)
              case cause: Exception =>
                error(s"$domainId: Unable to authenticate a user via jwt.", cause)
                Left(None)
            }
            .getOrElse(Left(None))
        } else {
          Left(Some(MaintenanceModeMessage))
        }
      }
      .getOrElse(Left(None))
  }

  private[this] def getJWTPublicKey(keyId: String): Option[(PublicKey, Boolean)] = {
    val (keyPem, admin) = if (AuthenticationHandler.AdminKeyId.equals(keyId)) {
      domainConfigStore.getAdminKeyPair() match {
        case Success(keyPair) => (Some(keyPair.publicKey), true)
        case _ =>
          logger.error(s"$domainId: Unable to load admin key for domain")
          (None, false)
      }
    } else {
      jwtKeyStore.getKey(keyId) match {
        case Success(Some(key)) if key.enabled => (Some(key.key), false)
        case _ => (None, false)
      }
    }

    keyPem.flatMap { pem =>
      TryWithResource(new PEMParser(new StringReader(pem))) { pemReader =>
        val spec = new X509EncodedKeySpec(pemReader.readPemObject().getContent)
        val keyFactory = KeyFactory.getInstance("RSA", new BouncyCastleProvider())
        Some((keyFactory.generatePublic(spec), admin))
      }.recoverWith {
        case e: Throwable =>
          logger.warn(s"$domainId: Unable to decode jwt public key: " + e.getMessage)
          Success(None)
      }.get
    }
  }

  private[this] def authenticateJwtWithPublicKey(authRequest: JwtAuthRequest, publicKey: PublicKey, admin: Boolean): Try[DomainActor.ConnectionSuccess] = {
    val jwtConsumer = new JwtConsumerBuilder()
      .setRequireExpirationTime()
      .setAllowedClockSkewInSeconds(AuthenticationHandler.AllowedClockSkew)
      .setRequireSubject()
      .setExpectedAudience(JwtConstants.Audience)
      .setVerificationKey(publicKey)
      .build()

    Try(jwtConsumer.processToClaims(authRequest.jwt)) flatMap { jwtClaims =>
      val username = jwtClaims.getSubject

      // FIXME in theory we should cache the token id for longer than the expiration to make
      //   sure a replay attack is not possible

      val (exists, userType) = if (admin) {
        (userStore.convergenceUserExists(username), DomainUserType.Convergence)
      } else {
        (userStore.domainUserExists(username), DomainUserType.Normal)
      }

      val userId = DomainUserId(userType, username)

      exists flatMap {
        case true =>
          logger.debug(s"$domainId: User specified in JWT already exists, updating with latest claims.")
          updateUserFromJwt(userId, jwtClaims)
        case false =>
          logger.debug(s"$domainId: User specified in JWT does not exist exist, Auto creating user.")
          lazyCreateUserFromJWT(userId, jwtClaims)
      } flatMap { _ =>
        authSuccess(userId, None) map { response =>
          updateLastLogin(userId)
          response
        }
      }
    }
  }

  private[this] def updateUserFromJwt(userId: DomainUserId, jwtClaims: JwtClaims): Try[Unit] = {
    val JwtInfo(_, firstName, lastName, displayName, email, groups) = JwtUtil.parseClaims(jwtClaims)
    val update = UpdateDomainUser(userId, firstName, lastName, displayName, email, None)
    for {
      _ <- userStore.updateDomainUser(update)
      _ <- groups match {
        case Some(g) => userGroupStore.setGroupsForUser(userId, g)
        case None => Success(())
      }
    } yield ()
  }

  private[this] def lazyCreateUserFromJWT(userId: DomainUserId, jwtClaims: JwtClaims): Try[String] = {
    val JwtInfo(username, firstName, lastName, displayName, email, groups) = JwtUtil.parseClaims(jwtClaims)
    (userId.userType match {
      case DomainUserType.Convergence =>
        userStore.createAdminDomainUser(username)
      case DomainUserType.Normal =>
        val newUser = CreateNormalDomainUser(username, firstName, lastName, displayName, email)
        for {
          username <- userStore.createNormalDomainUser(newUser)
          _ <- groups match {
            case Some(g) => userGroupStore.setGroupsForUser(userId, g)
            case None => Success(())
          }
        } yield username
      case DomainUserType.Anonymous =>
        Failure(new IllegalArgumentException("Can not authenticate an anonymous user via JWT"))
    }).recoverWith {
      case _: DuplicateValueException =>
        logger.warn(s"$domainId: Attempted to auto create user, but user already exists, returning auth success.")
        Success(username)
      case e: InvalidValueException =>
        Failure(new IllegalArgumentException(s"$domainId: Lazy creation of user based on JWT authentication failed: $username", e))
    }
  }

  //
  // Common Auth Success handling
  //

  private[this] def authSuccess(userId: DomainUserId, reconnectToken: Option[String]): Try[DomainActor.ConnectionSuccess] = {
    logger.debug(s"$domainId: Creating session after authentication success.")
    sessionStore.nextSessionId flatMap { sessionId =>
      reconnectToken match {
        case Some(reconnectToken) =>
          Success(DomainActor.ConnectionSuccess(DomainSessionAndUserId(sessionId, userId), Some(reconnectToken)))
        case None =>
          logger.debug(s"$domainId: Creating reconnect token.")
          userStore.createReconnectToken(userId, Duration.ofHours(24L)) map { token =>
            logger.debug(s"$domainId: Returning auth success.")
            DomainActor.ConnectionSuccess(session.DomainSessionAndUserId(sessionId, userId), Some(token))
          } recover {
            case error: Throwable =>
              logger.error(s"$domainId: Unable to create reconnect token", error)
              DomainActor.ConnectionSuccess(session.DomainSessionAndUserId(sessionId, userId), None)
          }
      }
    }
  }

  private[this] def updateLastLogin(userId: DomainUserId): Unit = {
    userStore.setLastLoginForUser(userId, Instant.now()) recover {
      case e => logger.warn("Unable to update last login time for user.", e)
    }
  }
}
