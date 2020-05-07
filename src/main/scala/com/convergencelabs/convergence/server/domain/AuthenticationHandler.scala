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

package com.convergencelabs.convergence.server.domain

import java.io.StringReader
import java.security.spec.X509EncodedKeySpec
import java.security.{KeyFactory, PublicKey}
import java.time.Instant

import com.convergencelabs.convergence.server.datastore.domain.DomainUserStore.{CreateNormalDomainUser, UpdateDomainUser}
import com.convergencelabs.convergence.server.datastore.domain._
import com.convergencelabs.convergence.server.datastore.{DuplicateValueException, InvalidValueException}
import com.convergencelabs.convergence.server.util.TryWithResource
import grizzled.slf4j.Logging
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.openssl.PEMParser
import org.jose4j.jwt.JwtClaims
import org.jose4j.jwt.consumer.{InvalidJwtException, JwtConsumerBuilder}

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success, Try}

object AuthenticationHandler {
  val AdminKeyId = "ConvergenceAdminKey"
  val AllowedClockSkew = 30
}

class AuthenticationHandler(private[this] val domainFqn: DomainId,
                            private[this] val domainConfigStore: DomainConfigStore,
                            private[this] val keyStore: JwtAuthKeyStore,
                            private[this] val userStore: DomainUserStore,
                            private[this] val userGroupStore: UserGroupStore,
                            private[this] val sessionStore: SessionStore,
                            private[this] implicit val ec: ExecutionContext)
  extends Logging {

  def authenticate(request: AuthenticationCredentials): Try[AuthenticationResponse] = {
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
  }

  //
  // Reconnect Auth
  //

  private[this] def authenticateReconnectToken(reconnectRequest: ReconnectTokenAuthRequest): Try[AuthenticationResponse] = {
    userStore.validateReconnectToken(reconnectRequest.token) flatMap {
      case Some(userId) =>
        authSuccess(userId, Some(reconnectRequest.token))
      case None =>
        Success(AuthenticationFailure)
    } recoverWith {
      case cause =>
        Failure(AuthenticationError(s"$domainFqn: Unable to authenticate a user via reconnect token.", cause))
    }
  }

  //
  // Anonymous Auth
  //

  private[this] def authenticateAnonymous(authRequest: AnonymousAuthRequest): Try[AuthenticationResponse] = {
    val AnonymousAuthRequest(displayName) = authRequest
    debug(s"$domainFqn: Processing anonymous authentication request with display name: $displayName")
    domainConfigStore.isAnonymousAuthEnabled() flatMap {
      case false =>
        debug(s"$domainFqn: Anonymous auth is disabled; returning AuthenticationFailure.")
        Success(AuthenticationFailure)
      case true =>
        debug(s"$domainFqn: Anonymous auth is enabled; creating anonymous user.")
        userStore.createAnonymousDomainUser(displayName) flatMap { username =>
          debug(s"$domainFqn: Anonymous user created: $username")
          val userId = DomainUserId(DomainUserType.Anonymous, username)
          authSuccess(userId, None)
        }
    } recoverWith {
      case cause: Throwable =>
        Failure(AuthenticationError(s"$domainFqn: Anonymous authentication error", cause))
    }
  }

  //
  // Password Auth
  //
  private[this] def authenticatePassword(authRequest: PasswordAuthRequest): Try[AuthenticationResponse] = {
    logger.debug(s"$domainFqn: Authenticating by username and password")
    userStore.validateCredentials(authRequest.username, authRequest.password) flatMap {
      case true =>
        val userId = DomainUserId(DomainUserType.Normal, authRequest.username)
        authSuccess(userId, None) map { response =>
          updateLastLogin(userId)
          response
        }
      case false =>
        Success(AuthenticationFailure)
    } recoverWith {
      case cause: Throwable =>
        Failure(AuthenticationError(s"$domainFqn: Unable to authenticate a user", cause))
    }
  }

  //
  // JWT Auth
  //
  private[this] def authenticateJwt(authRequest: JwtAuthRequest): Try[AuthenticationResponse] = {
    // This implements a two pass approach to be able to get the key id.
    val firstPassJwtConsumer = new JwtConsumerBuilder()
      .setSkipAllValidators()
      .setDisableRequireSignature()
      .setSkipSignatureVerification()
      .build()

    val jwtContext = firstPassJwtConsumer.process(authRequest.jwt)
    val objects = jwtContext.getJoseObjects
    val keyId = objects.get(0).getKeyIdHeaderValue

    getJWTPublicKey(keyId) match {
      case Some((publicKey, admin)) =>
        authenticateJwtWithPublicKey(authRequest, publicKey, admin)
      case None =>
        logger.warn(s"$domainFqn: Request to authenticate via token, with an invalid keyId: $keyId")
        Success(AuthenticationFailure)
    }
  }

  private[this] def getJWTPublicKey(keyId: String): Option[(PublicKey, Boolean)] = {
    val (keyPem, admin) = if (AuthenticationHandler.AdminKeyId.equals(keyId)) {
      domainConfigStore.getAdminKeyPair() match {
        case Success(keyPair) => (Some(keyPair.publicKey), true)
        case _ =>
          logger.error(s"$domainFqn: Unable to load admin key for domain")
          (None, false)
      }
    } else {
      keyStore.getKey(keyId) match {
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
          logger.warn(s"$domainFqn: Unable to decode jwt public key: " + e.getMessage)
          Success(None)
      }.get
    }
  }

  private[this] def authenticateJwtWithPublicKey(authRequest: JwtAuthRequest, publicKey: PublicKey, admin: Boolean): Try[AuthenticationResponse] = {
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
          logger.debug(s"$domainFqn: User specified in JWT already exists, updating with latest claims.")
          updateUserFromJwt(userId, jwtClaims)
        case false =>
          logger.debug(s"$domainFqn: User specified in JWT does not exist exist, Auto creating user.")
          lazyCreateUserFromJWT(userId, jwtClaims)
      } flatMap { _ =>
        authSuccess(userId, None) map { response =>
          updateLastLogin(userId)
          response
        }
      }
    } recoverWith {
      case cause: InvalidJwtException =>
        logger.debug(s"Invalid JWT: ${cause.getMessage}")
        Success(AuthenticationFailure)
      case cause: Exception =>
        Failure(AuthenticationError(s"$domainFqn: Unable to authenticate a user via jwt.", cause))
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
        logger.warn(s"$domainFqn: Attempted to auto create user, but user already exists, returning auth success.")
        Success(username)
      case e: InvalidValueException =>
        Failure(new IllegalArgumentException(s"$domainFqn: Lazy creation of user based on JWT authentication failed: $username", e))
    }
  }

  //
  // Common Auth Success handling
  //

  private[this] def authSuccess(userId: DomainUserId, reconnectToken: Option[String]): Try[AuthenticationSuccess] = {
    logger.debug(s"$domainFqn: Creating session after authentication success.")
    sessionStore.nextSessionId flatMap { sessionId =>
      reconnectToken match {
        case Some(reconnectToken) =>
          Success(AuthenticationSuccess(DomainUserSessionId(sessionId, userId), Some(reconnectToken)))
        case None =>
          logger.debug(s"$domainFqn: Creating reconnect token.")
          userStore.createReconnectToken(userId) map { token =>
            logger.debug(s"$domainFqn: Returning auth success.")
            AuthenticationSuccess(DomainUserSessionId(sessionId, userId), Some(token))
          } recover {
            case error: Throwable =>
              logger.error(s"$domainFqn: Unable to create reconnect token", error)
              AuthenticationSuccess(DomainUserSessionId(sessionId, userId), None)
          }
      }
    }
  }

  private[this] def updateLastLogin(userId: DomainUserId): Unit = {
    userStore.setLastLogin(userId, Instant.now()) recover {
      case e => logger.warn("Unable to update last login time for user.", e)
    }
  }
}
