package com.convergencelabs.server.domain.auth

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
import com.convergencelabs.server.datastore.domain.DomainPersistenceProvider
import akka.actor.Props

object AuthManagerActor {
  val RelativePath = "authManager"
  val AdminKeyId = "ConvergenceAdminUIKey"

  def props(
    domainConfig: DomainConfig,
    persistenceProvider: DomainPersistenceProvider,
    internalAuthProvider: InternalDomainAuthenticationProvider): Props =
    Props(new AuthManagerActor(
      domainConfig,
      persistenceProvider,
      internalAuthProvider))
}

class AuthManagerActor(
  private[this] val domainConfig: DomainConfig,
  private[this] val persistenceProvider: DomainPersistenceProvider,
  private[this] val internalAuthProvider: InternalDomainAuthenticationProvider)
    extends Actor with ActorLogging {

  private[this] implicit val ec = context.dispatcher

  def receive = {
    case message: PasswordAuthRequest => authenticatePassword(message)
    case message: TokenAuthRequest => authenticateToken(message)
  }

  def authenticatePassword(authRequest: PasswordAuthRequest): Unit = {
    val asker = sender()

    val f = internalAuthProvider.verfifyCredentials(
      domainConfig.domainFqn,
      authRequest.username,
      authRequest.password)

    f onComplete {
      case Success(verified) if verified => nofifyAuthSuccess(asker, authRequest.username)
      case Success(verified) if !verified => notifyAuthFailure(asker)
      case Failure(e) =>
        log.error(e, "Error authenticating user against LDAP")
        notifyAuthFailure(asker)
    }
  }

  private[this] def authenticateToken(authRequest: TokenAuthRequest): Unit = {

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
        notifyAuthFailure(sender())
        return
      }

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

      if (!persistenceProvider.userStore.domainUserExists(username)) {
        createUserFromJWT(jwtClaims)
      }

      nofifyAuthSuccess(sender(), username)

      // TODO in theory we should cache the token id for longer than the expiration to make
      // sure a replay attack is not possible.
    } catch {
      case e: InvalidJwtException =>
        log.debug("Authentication failed due to an invalid token.", e)
        notifyAuthFailure(sender())
      case e: Exception =>
        log.error("Error handling token based authentication request.", e)
        notifyAuthFailure(sender())
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
    this.persistenceProvider.userStore.createDomainUser(newUser)
  }

  private[this] def getJWTPublicKey(keyId: String): Option[PublicKey] = {
    var publicKey: Option[PublicKey] = None
    var keyPem: String = null

    if (!AuthManagerActor.AdminKeyId.equals(keyId)) {
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
          log.warning("Unabled to decode jwt public key: " + e.getMessage)
      } finally {
        pemReader.close()
      }
    }

    return publicKey
  }

  private[this] def nofifyAuthSuccess(asker: ActorRef, username: String): Unit = asker ! AuthSuccess(username)
  private[this] def notifyAuthFailure(asker: ActorRef): Unit = asker ! AuthFailure
}