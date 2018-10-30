package com.convergencelabs.server.domain

import java.io.StringReader
import java.security.KeyFactory
import java.security.PublicKey
import java.security.spec.X509EncodedKeySpec
import java.time.Instant

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success
import scala.util.Try

import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.openssl.PEMParser
import org.jose4j.jwt.JwtClaims
import org.jose4j.jwt.consumer.JwtConsumerBuilder

import com.convergencelabs.server.datastore.DuplicateValueException
import com.convergencelabs.server.datastore.InvalidValueExcpetion
import com.convergencelabs.server.datastore.domain.DomainConfigStore
import com.convergencelabs.server.datastore.domain.DomainUserStore
import com.convergencelabs.server.datastore.domain.DomainUserStore.CreateNormalDomainUser
import com.convergencelabs.server.datastore.domain.JwtAuthKeyStore
import com.convergencelabs.server.domain.model.SessionKey
import com.convergencelabs.server.util.TryWithResource
import com.convergencelabs.server.util.concurrent.FutureUtils.tryToFuture

import grizzled.slf4j.Logging
import com.convergencelabs.server.datastore.domain.SessionStore
import com.convergencelabs.server.datastore.domain.DomainUserStore.UpdateDomainUser
import com.convergencelabs.server.datastore.domain.UserGroupStore
import com.convergencelabs.server.datastore.domain.schema.DomainSchema

object AuthenticationHandler {
  val AdminKeyId = "ConvergenceAdminKey"
  val AllowedClockSkew = 30
}

class AuthenticationHandler(
  private[this] val domainFqn: DomainFqn,
  private[this] val domainConfigStore: DomainConfigStore,
  private[this] val keyStore: JwtAuthKeyStore,
  private[this] val userStore: DomainUserStore,
  private[this] val userGroupStore: UserGroupStore,
  private[this] val sessionStore: SessionStore,
  private[this] implicit val ec: ExecutionContext)
  extends Logging {

  def authenticate(request: AuthetncationCredentials): Future[AuthenticationResponse] = {
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

  private[this] def authenticateAnonymous(authRequest: AnonymousAuthRequest): Future[AuthenticationResponse] = {
    val AnonymousAuthRequest(displayName) = authRequest;
    debug(s"${domainFqn}: Processing anonymous authentication request with display name: ${displayName}")
    val result = domainConfigStore.isAnonymousAuthEnabled() flatMap {
      case false =>
        debug(s"${domainFqn}: Anonymous auth is disabled, so returning AuthenticationFailure")
        Success(AuthenticationFailure)
      case true =>
        debug(s"${domainFqn}: Anonymous auth is enabled, creating anonymous user...")
        userStore.createAnonymousDomainUser(displayName) flatMap { username =>
          debug(s"${domainFqn}: Anonymous user created: ${username}")
          authSuccess(username, false, None)
        }
    } recover {
      case cause: Throwable =>
        error(s"${domainFqn}: Anonymous authentication error", cause)
        AuthenticationError
    }

    tryToFuture(result)
  }

  private[this] def authenticatePassword(authRequest: PasswordAuthRequest): Future[AuthenticationResponse] = {
    logger.debug(s"${domainFqn}: Authenticating by username and password")
    val response = userStore.validateCredentials(authRequest.username, authRequest.password) match {
      case Success(true) => {
        authSuccess(authRequest.username, false, None) match {
          case Success(authSuccessResponse) =>
            updateLastLogin(authRequest.username, DomainUserType.Normal)
            authSuccessResponse
          case Failure(cause) => {
            logger.error(s"${domainFqn}: Unable to authenticate a user", cause)
            AuthenticationError
          }
        }
      }
      case Success(false) =>
        AuthenticationFailure
      case Failure(cause) => {
        logger.error(s"${domainFqn}: Unable to authenticate a user", cause)
        AuthenticationError
      }
    }

    Future.successful(response)
  }

  private[this] def authenticateJwt(authRequest: JwtAuthRequest): Future[AuthenticationResponse] = {
    Future[AuthenticationResponse] {
      // This implements a two pass approach to be able to get the key id.
      val firstPassJwtConsumer = new JwtConsumerBuilder()
        .setSkipAllValidators()
        .setDisableRequireSignature()
        .setSkipSignatureVerification()
        .build()

      val jwtContext = firstPassJwtConsumer.process(authRequest.jwt)
      val objects = jwtContext.getJoseObjects()
      val keyId = objects.get(0).getKeyIdHeaderValue()

      getJWTPublicKey(keyId) match {
        case Some((publicKey, admin)) =>
          authenticateJwtWithPublicKey(authRequest, publicKey, admin)
        case None =>
          logger.warn(s"${domainFqn}: Request to authenticate via token, with an invalid keyId: ${keyId}")
          AuthenticationFailure
      }
    }
  }

  private[this] def authenticateJwtWithPublicKey(authRequest: JwtAuthRequest, publicKey: PublicKey, admin: Boolean): AuthenticationResponse = {
    val jwtConsumer = new JwtConsumerBuilder()
      .setRequireExpirationTime()
      .setAllowedClockSkewInSeconds(AuthenticationHandler.AllowedClockSkew)
      .setRequireSubject()
      .setExpectedAudience(JwtConstants.Audiance)
      .setVerificationKey(publicKey)
      .build()

    val jwtClaims = jwtConsumer.processToClaims(authRequest.jwt)
    val username = jwtClaims.getSubject()

    // FIXME in theory we should cache the token id for longer than the expiration to make
    // sure a replay attack is not possible

    val exists = admin match {
      case true => userStore.adminUserExists(username)
      case false => userStore.domainUserExists(username)
    }

    val resolvedUsername = admin match {
      case true => DomainUserStore.adminUsername(username)
      case false => username
    }

    exists.flatMap {
      case true =>
        logger.debug(s"${domainFqn}: User specificed in JWT already exists, returning auth success.")
        this.updateUserFromJwt(jwtClaims, admin)
        authSuccess(resolvedUsername, admin, None)
      case false =>
        logger.debug(s"${domainFqn}: User specificed in JWT does not exist exist, auto creating user.")
        createUserFromJWT(jwtClaims, admin) flatMap { _ =>
          authSuccess(resolvedUsername, admin, None)
        } recoverWith {
          case e: DuplicateValueException =>
            if (e.field == DomainSchema.Classes.User.Fields.Username) {
              // The duplicate value case is when a race condition occurs between when we looked up the
              // user and then tried to create them.
              logger.warn(s"${domainFqn}: Attempted to auto create user, but user already exists, returning auth success.")
              authSuccess(resolvedUsername, admin, None)
            } else {
              logger.warn(s"${domainFqn}: Attempted to auto create user, but the email specified in the JWT is already registered.")
              Success(AuthenticationError)
            }
          case e: InvalidValueExcpetion =>
            Failure(new IllegalArgumentException(s"${domainFqn}: Lazy creation of user based on JWT authentication failed: {$username}", e))
        }
    }.recover {
      case cause: Exception =>
        logger.error(s"${domainFqn}: Unable to authenticate a user via token.", cause)
        AuthenticationError
    }.get
  }

  private[this] def authenticateReconnectToken(reconnectRequest: ReconnectTokenAuthRequest): Future[AuthenticationResponse] = {
    Future[AuthenticationResponse] {
      userStore.validateReconnectToken(reconnectRequest.token) match {
        case Success(username) =>
          if (username.isDefined) {
            authSuccess(username.get, false, Some(reconnectRequest.token)) match {
              case Success(authSuccessResponse) =>
                authSuccessResponse
              case Failure(cause) =>
                logger.error(s"${domainFqn}: Unable to authenticate a user via reconnect token.", cause)
                AuthenticationError
            }
          } else {
            AuthenticationError
          }
        case Failure(cause) =>
          logger.error(s"${domainFqn}: Unable to validate reconnect token.", cause)
          AuthenticationError
      }
    }
  }

  private[this] def authSuccess(username: String, admin: Boolean, reconnectToken: Option[String]): Try[AuthenticationResponse] = {
    logger.debug(s"${domainFqn}: Creating session after authenication success.")
    sessionStore.nextSessionId flatMap { id =>
      reconnectToken match {
        case Some(reconnectToken) =>
          Success(AuthenticationSuccess(username, SessionKey(username, id, admin), reconnectToken))
        case None =>
          logger.debug(s"${domainFqn}: Creating reconnect token.")
          userStore.createReconnectToken(username) map { token =>
            logger.debug(s"${domainFqn}: Returning auth success.")
            AuthenticationSuccess(username, SessionKey(username, id, admin), token)
          } recover {
            case error: Throwable =>
              logger.error(s"${domainFqn}: Unable to create reconnect token", error)
              AuthenticationSuccess(username, SessionKey(username, id, admin), "-1")
          }
      }
    }
  }

  private[this] def updateUserFromJwt(jwtClaims: JwtClaims, admin: Boolean): Try[Unit] = {
    val JwtInfo(username, firstName, lastName, displayName, email, groups) = JwtUtil.parseClaims(jwtClaims)
    if (admin) {
      Success(())
    } else {
      val update = UpdateDomainUser(username, firstName, lastName, displayName, email)
      for {
        _ <- userStore.updateDomainUser(update)
        _ <- groups match {
          case Some(g) => userGroupStore.setGroupsForUser(username, g)
          case None => Success(())
        }
      } yield (())
    }
  }

  private[this] def createUserFromJWT(jwtClaims: JwtClaims, admin: Boolean): Try[String] = {
    val JwtInfo(username, firstName, lastName, displayName, email, groups) = JwtUtil.parseClaims(jwtClaims)
    admin match {
      case true =>
        userStore.createAdminDomainUser(username)
      case false =>
        val newUser = CreateNormalDomainUser(username, firstName, lastName, displayName, email)
        for {
          username <- userStore.createNormalDomainUser(newUser)
          _ <- groups match {
            case Some(g) => userGroupStore.setGroupsForUser(username, g)
            case None => Success(())
          }
        } yield (username)
    }
  }

  private[this] def getJWTPublicKey(keyId: String): Option[(PublicKey, Boolean)] = {
    val (keyPem, admin) = if (AuthenticationHandler.AdminKeyId.equals(keyId)) {
      domainConfigStore.getAdminKeyPair() match {
        case Success(keyPair) => (Some(keyPair.publicKey), true)
        case _ =>
          logger.error(s"${domainFqn}: Unabled to load admin key for domain")
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
        val spec = new X509EncodedKeySpec(pemReader.readPemObject().getContent())
        val keyFactory = KeyFactory.getInstance("RSA", new BouncyCastleProvider())
        Some((keyFactory.generatePublic(spec), admin))
      }.recoverWith {
        case e: Throwable =>
          logger.warn(s"${domainFqn}: Unabled to decode jwt public key: " + e.getMessage)
          Success(None)
      }.get
    }
  }

  private[this] def updateLastLogin(username: String, userType: DomainUserType.Value): Unit = {
    userStore.setLastLogin(username, userType, Instant.now())
  }
}
