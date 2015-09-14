package com.convergencelabs.server.domain

import scala.collection.mutable
import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.Props
import akka.actor.Cancellable
import com.convergencelabs.server.datastore.DomainConfig
import com.convergencelabs.server.ErrorMessage
import com.convergencelabs.server.domain.model.ModelManagerActor
import com.convergencelabs.server.ProtocolConfiguration
import java.util.concurrent.TimeUnit
import scala.concurrent.duration._
import org.jose4j.jwt.consumer.JwtConsumerBuilder
import org.jose4j.jwt.consumer.InvalidJwtException
import java.io.IOException
import org.jose4j.jwt.MalformedClaimException
import scala.util.control.NonFatal
import scala.collection.mutable.ListBuffer
import com.convergencelabs.server.datastore.domain.DomainUser
import org.jose4j.jwt.JwtClaims
import java.security.PublicKey
import java.io.StringReader
import java.security.spec.X509EncodedKeySpec
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.openssl.PEMParser
import java.security.KeyFactory
import java.security.NoSuchAlgorithmException
import java.security.spec.InvalidKeySpecException
import com.convergencelabs.server.domain.auth.AuthManagerActor
import com.convergencelabs.server.datastore.domain.DomainPersistenceProvider
import com.convergencelabs.server.datastore.ConfigurationStore
import com.convergencelabs.server.domain.auth.LdapDomainAuthenticationProvider

// FIXME protocol config is silly.
object DomainActor {
  def props(
    domainManagerActor: ActorRef,
    domainConfig: DomainConfig,
    domainPersistence: DomainPersistenceProvider,
    configStore: ConfigurationStore,
    protocolConfig: ProtocolConfiguration,
    shutdownDelay: FiniteDuration): Props = Props(
    new DomainActor(
      domainManagerActor,
      domainConfig,
      domainPersistence,
      configStore,
      protocolConfig))
}

/**
 * The [[com.convergencelabs.server.domain.DomainActor]] is the supervisor for
 * all actor that comprise the services provided by a particular domain.
 */
class DomainActor(
    domainManagerActor: ActorRef,
    domainConfig: DomainConfig,
    domainPersistence: DomainPersistenceProvider,
    configStore: ConfigurationStore,
    protocolConfig: ProtocolConfiguration) extends Actor with ActorLogging {

  log.debug("Domain startting up: {}", domainConfig.domainFqn)

  private[this] implicit val ec = context.dispatcher

  private[this] val internalDomainAuthProvider = new LdapDomainAuthenticationProvider(configStore)

  private[this] val modelManagerActorRef = context.actorOf(ModelManagerActor.props(
    domainConfig.domainFqn,
    domainPersistence,
    protocolConfig),
    ModelManagerActor.RelativePath)

  private[this] val authActorRef = context.actorOf(AuthManagerActor.props(
    domainConfig,
    domainPersistence,
    internalDomainAuthProvider),
    AuthManagerActor.RelativePath)

  log.debug("Domain start up complete: {}", domainConfig.domainFqn)

  private[this] val connectedClients = mutable.Set[ActorRef]()

  def receive = {
    case message: HandshakeRequest => onClientConnect(message)
    case message: ClientDisconnected => onClientDisconnect(message)
    case message => unhandled(message)
  }

  private[this] def onClientConnect(message: HandshakeRequest): Unit = {
    log.debug("Client connected to domain: " + message.sessionId)
    this.connectedClients += message.clientActor
    sender ! HandshakeSuccess(self)
  }

  private[this] def onClientDisconnect(message: ClientDisconnected): Unit = {
    log.debug(s"Client disconnecting: ${message.sessionId}")

    connectedClients.remove(sender())
    if (connectedClients.isEmpty) {
      log.debug(s"Last client disconnected from domain: ${domainConfig.domainFqn}")

      domainManagerActor ! DomainShutdownRequest(domainConfig.domainFqn)
    }
  }

  override def postStop(): Unit = {
    log.debug("Domain(${domainConfig.domainFqn}) received shutdown command.  Shutting down.")
    domainPersistence.dispose()
  }
}