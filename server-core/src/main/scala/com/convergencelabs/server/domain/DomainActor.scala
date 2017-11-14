package com.convergencelabs.server.domain

import java.time.Instant

import scala.collection.mutable
import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.FiniteDuration
import scala.util.Failure
import scala.util.Success

import com.convergencelabs.server.ProtocolConfiguration
import com.convergencelabs.server.datastore.domain.DomainPersistenceManager
import com.convergencelabs.server.datastore.domain.DomainPersistenceManagerActor
import com.convergencelabs.server.datastore.domain.DomainPersistenceProvider
import com.convergencelabs.server.datastore.domain.DomainSession
import com.convergencelabs.server.domain.model.ModelCreator
import com.convergencelabs.server.domain.model.ModelQueryManagerActor
import com.convergencelabs.server.domain.model.ModelPermissionResolver

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.OneForOneStrategy
import akka.actor.Props
import akka.actor.SupervisorStrategy.Resume
import akka.actor.Terminated
import akka.cluster.sharding.ClusterSharding
import akka.cluster.sharding.ClusterShardingSettings
import akka.cluster.sharding.ShardRegion
import com.convergencelabs.server.domain.chat.ChatChannelLookupActor
import com.convergencelabs.server.domain.chat.ChatChannelActor
import com.convergencelabs.server.domain.chat.ChatChannelSharding

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
      case e: Throwable => {
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
    new ModelPermissionResolver(),
    new ModelCreator()),
    ModelManagerActor.RelativePath)

  private[this] val userServiceActor = context.actorOf(IdentityServiceActor.props(
    domainFqn),
    IdentityServiceActor.RelativePath)

  private[this] val activityServiceActor = context.actorOf(ActivityServiceActor.props(
    domainFqn),
    ActivityServiceActor.RelativePath)

  private[this] val presenceServiceActor = context.actorOf(PresenceServiceActor.props(
    domainFqn),
    PresenceServiceActor.RelativePath)

  private[this] val chatChannelLookupActor = context.actorOf(ChatChannelLookupActor.props(
    domainFqn),
    ChatChannelLookupActor.RelativePath)

  private[this] val chatChannelRegion: ActorRef =
    ClusterSharding(context.system).start(
      typeName = ChatChannelSharding.calculateRegionName(domainFqn),
      entityProps = Props(classOf[ChatChannelActor], domainFqn),
      settings = ClusterShardingSettings(context.system),
      extractEntityId = ChatChannelSharding.extractEntityId,
      extractShardId = ChatChannelSharding.extractShardId)

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
        chatChannelLookupActor,
        chatChannelRegion)
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
          provider.userGroupStore,
          provider.sessionStore,
          context.dispatcher)
      case Failure(cause) =>
        log.error(cause, "Unable to obtain a domain persistence provider.")
    }
  }

  override def postStop(): Unit = {
    log.debug(s"Domain(${domainFqn}) received shutdown command.  Shutting down.")
    domainPersistenceManager.releasePersistenceProvider(self, context, domainFqn)
    chatChannelRegion ! ShardRegion.GracefulShutdown
  }
}
