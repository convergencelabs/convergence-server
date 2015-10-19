package com.convergencelabs.server.domain

import akka.actor.Actor
import akka.actor.ActorLogging
import com.convergencelabs.server.datastore.DomainConfig
import com.convergencelabs.server.datastore.ConfigurationStore
import scala.util.Success
import scala.util.Failure
import com.convergencelabs.server.ErrorMessage
import akka.actor.ActorRef
import org.bouncycastle.openssl.PEMParser
import java.security.spec.X509EncodedKeySpec
import java.io.StringReader
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.KeyFactory
import org.jose4j.jwt.consumer.JwtConsumerBuilder
import org.jose4j.jwt.consumer.InvalidJwtException
import org.jose4j.jwt.JwtClaims
import scala.collection.mutable.ListBuffer
import com.convergencelabs.server.datastore.domain.DomainUser
import java.security.PublicKey
import scala.collection.JavaConversions._
import akka.actor.Props
import com.convergencelabs.server.datastore.domain.DomainUserStore
import scala.concurrent.Future
import scala.concurrent.Promise
import grizzled.slf4j.Logging
import scala.concurrent.ExecutionContext

object AuthenticationHandler {
  val RelativePath = "authManager"
  val AdminKeyId = "ConvergenceAdminUIKey"
}

class AuthenticationHandler(
  private[this] val domainConfig: DomainConfig,
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
      case true => AuthenticationSuccess(authRequest.username)
      case false => AuthenticationFailure
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

          if (!userStore.domainUserExists(username)) {
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

    var emails = ListBuffer[String]()
    if (jwtClaims.hasClaim(JwtClaimConstants.Emails)) {
      val emailsClaim = jwtClaims.getClaimValue(JwtClaimConstants.Emails)
      emailsClaim match {
        case claim: java.util.List[_] => {
          claim.toList.filter(email => email.isInstanceOf[String]).foreach(email => emails += email.toString())
        }
      }
    }

    val newUser = DomainUser(null, username, firstName, lastName, emails.toList)
    userStore.createDomainUser(newUser)
  }

  private[this] def getJWTPublicKey(keyId: String): Option[PublicKey] = {
    var publicKey: Option[PublicKey] = None
    var keyPem: String = null

    if (!AuthenticationHandler.AdminKeyId.equals(keyId)) {
      val key = this.domainConfig.keys(keyId)
      if (key.enabled) {
        keyPem = key.key
      }
    } else {
      keyPem = this.domainConfig.adminKeyPair.publicKey
    }

    if (keyPem != null) {
      val pemReader = new PEMParser(new StringReader(keyPem))
      val spec = new X509EncodedKeySpec(pemReader.readPemObject().getContent())
      try {
        val keyFactory = KeyFactory.getInstance("RSA", new BouncyCastleProvider())
        publicKey = Some(keyFactory.generatePublic(spec))
      } catch {
        case e: Exception =>
          logger.warn("Unabled to decode jwt public key: " + e.getMessage)
      } finally {
        pemReader.close()
      }
    }

    return publicKey
  }

  private[this] def nofifyAuthSuccess(asker: ActorRef, username: String): Unit = asker ! AuthenticationSuccess(username)
  private[this] def notifyAuthFailure(asker: ActorRef): Unit = asker ! AuthenticationFailure
}