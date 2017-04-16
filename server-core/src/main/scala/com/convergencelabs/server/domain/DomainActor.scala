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
import akka.actor.OneForOneStrategy
import akka.actor.SupervisorStrategy._
import scala.concurrent.duration._
import com.convergencelabs.server.datastore.domain.DomainPersistenceManager
import com.convergencelabs.server.domain.model.ModelPermissionResolver

object DomainActor {
  def props(
    domainManagerActor: ActorRef,
    domainFqn: DomainFqn,
    protocolConfig: ProtocolConfiguration,
    shutdownDelay: FiniteDuration,
    domainPersistenceManager: DomainPersistenceManager): Props = Props(
    new DomainActor(
      domainManagerActor,
      domainFqn,
      protocolConfig,
      domainPersistenceManager))
}

/**
 * The [[com.convergencelabs.server.domain.DomainActor]] is the supervisor for
 * all actor that comprise the services provided by a particular domain.
 */
class DomainActor(
  domainManagerActor: ActorRef,
  domainFqn: DomainFqn,
  protocolConfig: ProtocolConfiguration,
  domainPersistenceManager: DomainPersistenceManager)
    extends Actor
    with ActorLogging {

  log.debug(s"Domain startting up: ${domainFqn}")

  override val supervisorStrategy =
  OneForOneStrategy(maxNrOfRetries = 10, withinTimeRange = 1.minute) {
    case e: Throwable          => {
      log.error(e, s"Actor at '${sender.path}' threw error")
      Resume
    }
  }
  
  private[this] var persistenceProvider: DomainPersistenceProvider = _
  private[this] implicit val ec = context.dispatcher

  private[this] val modelManagerActorRef = context.actorOf(ModelManagerActor.props(
    domainFqn,
    protocolConfig,
    DomainPersistenceManagerActor,
    new ModelPermissionResolver()),
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

    authenticator.authenticate(message.credentials) onComplete {
      case Success(AuthenticationSuccess(username, sk)) =>
        log.debug("Authenticated user successfully, creating session")
        
        val method = message.credentials match {
          case x: JwtAuthRequest => "jwt"
          case x: PasswordAuthRequest => "password"
          case x: AnonymousAuthRequest => "anonymous"
        }

        val session = DomainSession(
          sk.sid,
          username,
          connected,
          None,
          method,
          message.client,
          message.clientVersion,
          message.clientMetaData,
          message.remoteAddress)

        persistenceProvider.sessionStore.createSession(session) map { _ =>
            authenticatedClients += (message.clientActor -> sk.sid)
            asker ! AuthenticationSuccess(username, sk)
        } recover {
          case cause: Exception =>
            log.error(cause, "Unable to authenticate user because a session could not be created")
            asker ! AuthenticationFailure
        }
      case Success(AuthenticationFailure) =>
        asker ! AuthenticationFailure

      case Success(AuthenticationError) =>
        asker ! AuthenticationError

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
    val p = domainPersistenceManager.acquirePersistenceProvider(
      self, context, domainFqn)

    p match {
      case Success(provider) =>
        this.persistenceProvider = provider
        authenticator = new AuthenticationHandler(
          provider.configStore,
          provider.jwtAuthKeyStore,
          provider.userStore,
          provider.sessionStore,
          context.dispatcher)
      case Failure(cause) =>
        log.error(cause, "Unable to obtain a domain persistence provider.")
    }
  }

  override def postStop(): Unit = {
    log.debug(s"Domain(${domainFqn}) received shutdown command.  Shutting down.")
    domainPersistenceManager.releasePersistenceProvider(self, context, domainFqn)
  }
}
