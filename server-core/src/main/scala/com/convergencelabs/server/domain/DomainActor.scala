package com.convergencelabs.server.domain

import scala.collection.mutable
import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.Props
import akka.actor.Cancellable
import com.convergencelabs.server.domain.model.ModelManagerActor
import com.convergencelabs.server.ProtocolConfiguration
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration
import org.jose4j.jwt.consumer.JwtConsumerBuilder
import org.jose4j.jwt.consumer.InvalidJwtException
import java.io.IOException
import org.jose4j.jwt.MalformedClaimException
import scala.collection.mutable.ListBuffer
import org.jose4j.jwt.JwtClaims
import java.security.PublicKey
import com.convergencelabs.server.datastore.domain.DomainPersistenceProvider
import scala.util.Success
import scala.util.Failure
import com.convergencelabs.server.datastore.domain.DomainPersistenceManagerActor
import akka.pattern.Patterns
import com.convergencelabs.server.datastore.domain.AcquireDomainPersistence
import akka.util.Timeout
import scala.concurrent.Await
import com.convergencelabs.server.datastore.domain.DomainPersistenceResponse
import com.convergencelabs.server.datastore.domain.PersistenceProviderReference
import com.convergencelabs.server.datastore.domain.PersistenceProviderUnavailable
import com.convergencelabs.server.datastore.domain.DomainPersistenceProvider
import scala.util.Try
import akka.actor.Terminated
import java.time.Instant
import com.convergencelabs.server.datastore.domain.DomainSession

object DomainActor {
  def props(
    domainManagerActor: ActorRef,
    domainFqn: DomainFqn,
    protocolConfig: ProtocolConfiguration,
    shutdownDelay: FiniteDuration): Props = Props(
    new DomainActor(
      domainManagerActor,
      domainFqn,
      protocolConfig))
}

/**
 * The [[com.convergencelabs.server.domain.DomainActor]] is the supervisor for
 * all actor that comprise the services provided by a particular domain.
 */
class DomainActor(
  domainManagerActor: ActorRef,
  domainFqn: DomainFqn,
  protocolConfig: ProtocolConfiguration)
    extends Actor
    with ActorLogging {

  log.debug(s"Domain startting up: ${domainFqn}")

  private[this] var persistenceProvider: DomainPersistenceProvider = _
  private[this] implicit val ec = context.dispatcher

  private[this] val modelManagerActorRef = context.actorOf(ModelManagerActor.props(
    domainFqn,
    protocolConfig),
    ModelManagerActor.RelativePath)

  private[this] val userServiceActor = context.actorOf(UserServiceActor.props(
    domainFqn),
    UserServiceActor.RelativePath)

  private[this] val activityServiceActor = context.actorOf(ActivityServiceActor.props(
    domainFqn),
    ActivityServiceActor.RelativePath)

  private[this] val presenceServiceActor = context.actorOf(PresenceServiceActor.props(
    domainFqn),
    PresenceServiceActor.RelativePath)

  private[this] val chatServiceActor = context.actorOf(ChatServiceActor.props(
    domainFqn),
    ChatServiceActor.RelativePath)

  private[this] var authenticator: AuthenticationHandler = _

  log.debug(s"Domain start up complete: ${domainFqn}")

  private[this] val connectedClients = mutable.Set[ActorRef]()
  private[this] val authenticatedClients = mutable.Map[ActorRef, String]()

  def receive: Receive = {
    case message: HandshakeRequest => onHandshakeRequest(message)
    case message: AuthenticationRequest => onAuthenticationRequest(message)
    case message: ClientDisconnected => onClientDisconnect(message)
    case Terminated(client) => handleDeathWatch(client)
    case message: Any => unhandled(message)
  }

  private[this] def handleDeathWatch(actorRef: ActorRef): Unit = {
    if (this.connectedClients.contains(actorRef)) {
      log.debug(s"Client actor died, removing")
      removeClient(actorRef)
    }
  }

  private[this] def onAuthenticationRequest(message: AuthenticationRequest): Unit = {
    val asker = sender
    val connected = Instant.now()
    authenticator.authenticate(message) onComplete {
      case Success(response) =>
        response match {
          case AuthenticationSuccess(username, sk) =>
            val method = message match {
              case x: JwtAuthRequest => "jwt"
              case x: PasswordAuthRequest => "password"
              case x: AnonymousAuthRequest => "anonymous"
            }
            val session = DomainSession(sk.sid, username, connected, None, method, "javasript", "unknown", Map(), "unknown")
            persistenceProvider.sessionStore.createSession(session) match {
              case Success(_) =>
                authenticatedClients += (message.clientActor -> sk.sid)
                asker ! response
              case _ =>
                asker ! AuthenticationFailure
            }
          case _ =>
            asker ! response
        }
      case Failure(e) =>
        asker ! AuthenticationFailure
    }
  }

  private[this] def onHandshakeRequest(message: HandshakeRequest): Unit = {
    persistenceProvider.validateConnection() map { _ =>
      connectedClients += message.clientActor
      context.watch(message.clientActor)
      sender ! HandshakeSuccess(
        self,
        modelManagerActorRef,
        userServiceActor,
        activityServiceActor,
        presenceServiceActor,
        chatServiceActor)
    } recover {
      case cause: Exception =>
        log.error(cause, "Could not connect to domain database")
        sender ! HandshakeFailure("domain_unavailable", "Could not connect to database.")
    }
  }

  private[this] def onClientDisconnect(message: ClientDisconnected): Unit = {
    log.debug(s"Client disconnecting: ${message.sessionId}")
    removeClient(sender())
  }

  private[this] def removeClient(client: ActorRef): Unit = {
    connectedClients.remove(client)
    authenticatedClients.get(client) foreach { sessionId =>
      persistenceProvider.sessionStore.setSessionDisconneted(sessionId, Instant.now())
    }
    if (connectedClients.isEmpty) {
      log.debug(s"Last client disconnected from domain: ${domainFqn}")
      domainManagerActor ! DomainShutdownRequest(domainFqn)
    }
  }

  override def preStart(): Unit = {
    val p = DomainPersistenceManagerActor.acquirePersistenceProvider(
      self, context, domainFqn)

    p match {
      case Success(provider) =>
        this.persistenceProvider = provider
        authenticator = new AuthenticationHandler(
          provider.configStore,
          provider.jwtAuthKeyStore,
          provider.userStore,
          context.dispatcher)
      case Failure(cause) =>
        log.error(cause, "Unable to obtain a domain persistence provider.")
    }
  }

  override def postStop(): Unit = {
    log.debug(s"Domain(${domainFqn}) received shutdown command.  Shutting down.")
    DomainPersistenceManagerActor.releasePersistenceProvider(self, context, domainFqn)
  }
}
