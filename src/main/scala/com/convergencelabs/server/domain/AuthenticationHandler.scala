package com.convergencelabs.server.domain

import java.io.StringReader
import java.security.KeyFactory
import java.security.PublicKey
import java.security.spec.X509EncodedKeySpec

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.Promise
import scala.util.Failure
import scala.util.Success

import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.openssl.PEMParser
import org.jose4j.jwt.JwtClaims
import org.jose4j.jwt.consumer.InvalidJwtException
import org.jose4j.jwt.consumer.JwtConsumerBuilder

import com.convergencelabs.server.datastore.domain.DomainConfigStore
import com.convergencelabs.server.datastore.domain.DomainUser
import com.convergencelabs.server.datastore.domain.DomainUserStore
import com.convergencelabs.server.util.TryWithResource

import akka.actor.ActorRef
import akka.actor.actorRef2Scala
import grizzled.slf4j.Logging

object AuthenticationHandler {
  val RelativePath = "authManager"
  val AdminKeyId = "ConvergenceAdminUIKey"
}

class AuthenticationHandler(
  private[this] val domainConfigStore: DomainConfigStore,
  private[this] val userStore: DomainUserStore,
  private[this] implicit val ec: ExecutionContext)
    extends Logging {

  def authenticate(request: AuthenticationRequest): Future[AuthenticationResponse] = {
    request match {
      case message: PasswordAuthRequest => authenticatePassword(message)
      case message: TokenAuthRequest => authenticateToken(message)
    }
  }

  private[this] def authenticatePassword(authRequest: PasswordAuthRequest): Future[AuthenticationResponse] = {
    val promise = Promise[AuthenticationResponse]
    val response = userStore.validateCredentials(authRequest.username, authRequest.password) match {
      case Success(true) => AuthenticationSuccess(authRequest.username)
      case Success(false) => AuthenticationFailure
      case Failure(cause) => ??? //FIXME: Need to handle failed auth do to error  
    }

    Future.successful(response)
  }

  private[this] def authenticateToken(authRequest: TokenAuthRequest): Future[AuthenticationResponse] = {
    Future[AuthenticationResponse] {
      try {
        // This implements a two pass approach to be able to get the key id.
        val firstPassJwtConsumer = new JwtConsumerBuilder()
          .setSkipAllValidators()
          .setDisableRequireSignature()
          .setSkipSignatureVerification()
          .build()

        val jwtContext = firstPassJwtConsumer.process(authRequest.jwt)
        val objects = jwtContext.getJoseObjects()
        val keyId = objects.get(0).getKeyIdHeaderValue()
        val publicKey = getJWTPublicKey(keyId)

        if (publicKey.isEmpty) {
          AuthenticationFailure
        } else {

          val jwtConsumer = new JwtConsumerBuilder()
            .setRequireExpirationTime()
            .setAllowedClockSkewInSeconds(30)
            .setRequireSubject()
            .setExpectedIssuer("ConvergenceJWTGenerator")
            .setExpectedAudience("Convergence")
            .setVerificationKey(publicKey.get)
            .build()

          val jwtClaims = jwtConsumer.processToClaims(authRequest.jwt)

          val username = jwtClaims.getSubject()

          //FIXME: Handle Failure Case
          if (!userStore.domainUserExists(username).get) {
            createUserFromJWT(jwtClaims)
          }

          // TODO in theory we should cache the token id for longer than the expiration to make
          // sure a replay attack is not possible.
          AuthenticationSuccess(username)
        }
      } catch {
        case e: InvalidJwtException =>
          logger.debug("Authentication failed due to an invalid token.", e)
          AuthenticationFailure
        case e: Exception =>
          logger.error("Error handling token based authentication request.", e)
          AuthenticationFailure
      }
    }
  }

  private[this] def createUserFromJWT(jwtClaims: JwtClaims): Unit = {
    val username = jwtClaims.getSubject()

    var firstName = ""
    if (jwtClaims.hasClaim(JwtClaimConstants.FirstName)) {
      val firstNameClaim = jwtClaims.getClaimValue(JwtClaimConstants.FirstName)
      if (firstNameClaim != null && firstNameClaim.isInstanceOf[String]) {
        firstName = firstNameClaim.toString()
      }
    }

    var lastName = ""
    if (jwtClaims.hasClaim(JwtClaimConstants.LastName)) {
      val lastNameClaim = jwtClaims.getClaimValue(JwtClaimConstants.LastName)
      if (lastNameClaim != null && lastNameClaim.isInstanceOf[String]) {
        lastName = lastNameClaim.toString()
      }
    }

    val email = if (jwtClaims.hasClaim(JwtClaimConstants.Email)) {
      val emailClaim = jwtClaims.getClaimValue(JwtClaimConstants.Email)
      emailClaim match {
        case claim: String => claim
        case _ => null
      }
    } else {
      null
    }

    val newUser = DomainUser(null, username, firstName, lastName, email)
    userStore.createDomainUser(newUser, None)
  }

  private[this] def getJWTPublicKey(keyId: String): Option[PublicKey] = {
    val keyPem: Option[String] = if (!AuthenticationHandler.AdminKeyId.equals(keyId)) {
      domainConfigStore.getTokenKey(keyId) match {
        case Success(Some(key)) if key.enabled => Some(key.key)
        case _ => None // FIXME handle error?
      }
    } else {
      domainConfigStore.getAdminKeyPair() match {
        case Success(keyPair) => Some(keyPair.publicKey)
        case _ => None // FIXME handle error?
      }
    }
    
    keyPem.flatMap { pem =>
      TryWithResource( new PEMParser(new StringReader(pem))) { pemReader =>
        val spec = new X509EncodedKeySpec(pemReader.readPemObject().getContent())
        val keyFactory = KeyFactory.getInstance("RSA", new BouncyCastleProvider())
        Some(keyFactory.generatePublic(spec))
      } .recoverWith { case e =>
         logger.warn("Unabled to decode jwt public key: " + e.getMessage)
          Success(None)
      }.get
    }
  }

  private[this] def nofifyAuthSuccess(asker: ActorRef, username: String): Unit = asker ! AuthenticationSuccess(username)
  private[this] def notifyAuthFailure(asker: ActorRef): Unit = asker ! AuthenticationFailure
}