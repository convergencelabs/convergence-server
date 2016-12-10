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

import com.convergencelabs.server.datastore.DuplicateValueExcpetion
import com.convergencelabs.server.datastore.InvalidValueExcpetion
import com.convergencelabs.server.datastore.domain.DomainConfigStore
import com.convergencelabs.server.datastore.domain.DomainUserStore
import com.convergencelabs.server.datastore.domain.DomainUserStore.CreateNormalDomainUser
import com.convergencelabs.server.datastore.domain.JwtAuthKeyStore
import com.convergencelabs.server.domain.model.SessionKey
import com.convergencelabs.server.util.TryWithResource
import com.convergencelabs.server.util.concurrent.FutureUtils.tryToFuture

import grizzled.slf4j.Logging

object AuthenticationHandler {
  val AdminKeyId = "ConvergenceAdminKey"
  val AllowedClockSkew = 30
}

class AuthenticationHandler(
  private[this] val domainConfigStore: DomainConfigStore,
  private[this] val keyStore: JwtAuthKeyStore,
  private[this] val userStore: DomainUserStore,
  private[this] implicit val ec: ExecutionContext)
    extends Logging {

  def authenticate(request: AuthetncationCredentials): Future[AuthenticationResponse] = {
    request match {
      case message: PasswordAuthRequest => authenticatePassword(message)
      case message: JwtAuthRequest => authenticateToken(message)
      case message: AnonymousAuthRequest => authenticateAnonymous(message)
    }
  }

  private[this] def authenticateAnonymous(authRequest: AnonymousAuthRequest): Future[AuthenticationResponse] = {
    val AnonymousAuthRequest(displayName) = authRequest;

    val result = domainConfigStore.isAnonymousAuthEnabled() flatMap {
      case false =>
        Success(AuthenticationFailure)
      case true =>
        userStore.createAnonymousDomainUser(displayName) flatMap { username => authSuccess(username) }
    } recover {
      case e: Exception =>
        AuthenticationError
    }

    tryToFuture(result)
  }

  private[this] def authenticatePassword(authRequest: PasswordAuthRequest): Future[AuthenticationResponse] = {
    logger.debug("Authenticating by username and password")
    val response = userStore.validateCredentials(authRequest.username, authRequest.password) match {
      case Success(true) => {
        userStore.nextSessionId match {
          case Success(sessionId) =>
            updateLastLogin(authRequest.username, DomainUserType.Normal)
            AuthenticationSuccess(authRequest.username, SessionKey(authRequest.username, sessionId))
          case Failure(cause) => {
            logger.error("Unable to authenticate a user", cause)
            AuthenticationError
          }
        }
      }
      case Success(false) =>
        AuthenticationFailure
      case Failure(cause) => {
        logger.error("Unable to authenticate a user", cause)
        AuthenticationError
      }
    }

    Future.successful(response)
  }

  private[this] def authenticateToken(authRequest: JwtAuthRequest): Future[AuthenticationResponse] = {
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
          authenticateTokenWithPublicKey(authRequest, publicKey, admin)
        case None =>
          logger.warn(s"Request to authenticate via token, with an invalid keyId: ${keyId}")
          AuthenticationFailure
      }
    }
  }

  private[this] def authenticateTokenWithPublicKey(authRequest: JwtAuthRequest, publicKey: PublicKey, admin: Boolean): AuthenticationResponse = {
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
        logger.debug("User specificed in token already exists, returning auth success.")
        // FIXME We need to update the users info based on any provided claims.
        authSuccess(resolvedUsername)
      case false =>
        logger.debug("User specificed in token does not exist exist, creating.")
        createUserFromJWT(jwtClaims, admin) flatMap { _ =>
          authSuccess(resolvedUsername)
        } recoverWith {
          case e: DuplicateValueExcpetion =>
            // The duplicate value case is when a race condition occurs between when we looked up the
            // user and then tried to create them.
            logger.warn("Attempted to auto create user, but user already exists, returning auth success.")
            authSuccess(resolvedUsername)
          case e: InvalidValueExcpetion =>
            Failure(new IllegalArgumentException("Lazy creation of user based on JWT authentication failed: {$username}", e))
        }
    }.recover {
      case cause: Exception =>
        logger.error("Unable to authenticate a user via token.", cause)
        AuthenticationError
    }.get
  }

  private[this] def authSuccess(username: String): Try[AuthenticationResponse] = {
    userStore.nextSessionId map { id =>
      AuthenticationSuccess(username, SessionKey(username, id))
    }
  }

  private[this] def createUserFromJWT(jwtClaims: JwtClaims, admin: Boolean): Try[String] = {
    val username = jwtClaims.getSubject()
    admin match {
      case true =>
        userStore.createAdminDomainUser(username)
      case false =>
        val firstName = JwtUtil.getClaim[String](jwtClaims, JwtClaimConstants.FirstName)
        val lastName = JwtUtil.getClaim[String](jwtClaims, JwtClaimConstants.LastName)
        val displayName = JwtUtil.getClaim[String](jwtClaims, JwtClaimConstants.DisplayName)
        val email = JwtUtil.getClaim[String](jwtClaims, JwtClaimConstants.Email)
        val newUser = CreateNormalDomainUser(username, firstName, lastName, displayName, email)
        userStore.createNormalDomainUser(newUser)
    }
  }

  private[this] def getJWTPublicKey(keyId: String): Option[(PublicKey, Boolean)] = {
    val (keyPem, admin) = if (AuthenticationHandler.AdminKeyId.equals(keyId)) {
      domainConfigStore.getAdminKeyPair() match {
        case Success(keyPair) => (Some(keyPair.publicKey), true)
        case _ =>
          logger.error("Unabled to load admin key for domain")
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
          logger.warn("Unabled to decode jwt public key: " + e.getMessage)
          Success(None)
      }.get
    }
  }

  private[this] def updateLastLogin(username: String, userType: DomainUserType.Value): Unit = {
    userStore.setLastLogin(username, userType, Instant.now())
  }
}
