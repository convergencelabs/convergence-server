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

import com.convergencelabs.server.datastore.CreateResult
import com.convergencelabs.server.datastore.CreateSuccess
import com.convergencelabs.server.datastore.DuplicateValue
import com.convergencelabs.server.datastore.InvalidValue
import com.convergencelabs.server.datastore.domain.ApiKeyStore
import com.convergencelabs.server.datastore.domain.DomainConfigStore
import com.convergencelabs.server.datastore.domain.DomainUserStore
import com.convergencelabs.server.datastore.domain.DomainUserStore.CreateNormalDomainUser
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
  private[this] val keyStore: ApiKeyStore,
  private[this] val userStore: DomainUserStore,
  private[this] implicit val ec: ExecutionContext)
    extends Logging {

  def authenticate(request: AuthenticationRequest): Future[AuthenticationResponse] = {
    request match {
      case message: PasswordAuthRequest => authenticatePassword(message)
      case message: TokenAuthRequest => authenticateToken(message)
      case message: AnonymousAuthRequest => authenticateAnonymous(message)
    }
  }

  private[this] def authenticateAnonymous(authRequest: AnonymousAuthRequest): Future[AuthenticationResponse] = {
    val AnonymousAuthRequest(displayName) = authRequest;

    val result = domainConfigStore.isAnonymousAuthEnabled() flatMap {
      case false =>
        Success(AuthenticationFailure)
      case true =>
        userStore.createAnonymousDomainUser(displayName) flatMap {
          case CreateSuccess(username) =>
            userStore.nextSessionId map (id => AuthenticationSuccess(username, SessionKey(username, id)))
          case InvalidValue | DuplicateValue =>
            logger.error("Attempted to auto create user, but user already exists, returning auth success.")
            Success(AuthenticationError)
        }
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

  private[this] def authenticateToken(authRequest: TokenAuthRequest): Future[AuthenticationResponse] = {
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
          logger.warn("Request to authenticate via token, with an invalid keyId")
          AuthenticationFailure
      }
    }
  }

  private[this] def authenticateTokenWithPublicKey(authRequest: TokenAuthRequest, publicKey: PublicKey, admin: Boolean): AuthenticationResponse = {
    val jwtConsumer = new JwtConsumerBuilder()
      .setRequireExpirationTime()
      .setAllowedClockSkewInSeconds(AuthenticationHandler.AllowedClockSkew)
      .setRequireSubject()
      .setExpectedIssuer(JwtConstants.Issuer)
      .setExpectedAudience(JwtConstants.Audiance)
      .setVerificationKey(publicKey)
      .build()

    val jwtClaims = jwtConsumer.processToClaims(authRequest.jwt)
    val username = jwtClaims.getSubject()

    // FIXME in theory we should cache the token id for longer than the expiration to make
    // sure a replay attack is not possible

    val exists = admin match {
      case true => userStore.domainUserExists(username)
      case false => userStore.adminUserExists(username)
    }
    
    if (exists == null)
      println("\n\n###" + username)

    exists flatMap {
      case true =>
        logger.debug("User specificed in token already exists, returning auth success.")
        userStore.nextSessionId map (id => AuthenticationSuccess(username, SessionKey(username, id)))
      case false =>
        logger.error("User specificed in token does not exist exist, creating.")
        createUserFromJWT(jwtClaims, admin) flatMap {
          case CreateSuccess(_) | DuplicateValue =>
            // The duplicate value case is when a race condition occurs between when we looked up the
            // user and then tried to create them.
            logger.warn("Attempted to auto create user, but user already exists, returning auth success.")
            userStore.nextSessionId map (id => AuthenticationSuccess(username, SessionKey(username, id)))
          case InvalidValue =>
            Failure(new IllegalArgumentException("Lazy creation of user based on JWT authentication failed: {$username}"))
        }
    } match {
      case Success(response) =>
        response
      case Failure(cause) =>
        logger.error("Unable to authenticate a user via token.", cause)
        AuthenticationError
    }
  }

  private[this] def createUserFromJWT(jwtClaims: JwtClaims, admin: Boolean): Try[CreateResult[String]] = {
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
        userStore.createNormalDomainUser(newUser, None)
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
