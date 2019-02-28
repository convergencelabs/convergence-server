package com.convergencelabs.server.domain

import java.time.Instant

import scala.collection.mutable
import scala.concurrent.duration.Duration
import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.FiniteDuration
import scala.util.Failure
import scala.util.Success
import scala.util.Try

import com.convergencelabs.server.ProtocolConfiguration
import com.convergencelabs.server.actor.ShardedActor
import com.convergencelabs.server.actor.ShardedActorStatUpPlan
import com.convergencelabs.server.actor.StartUpNotRequired
import com.convergencelabs.server.actor.StartUpRequired
import com.convergencelabs.server.datastore.domain.DomainNotFoundException
import com.convergencelabs.server.datastore.domain.DomainPersistenceManager
import com.convergencelabs.server.datastore.domain.DomainPersistenceProvider
import com.convergencelabs.server.datastore.domain.DomainSession
import com.convergencelabs.server.datastore.domain.ModelOperationStoreActor
import com.convergencelabs.server.datastore.domain.ModelStoreActor
import com.convergencelabs.server.db.provision.DomainProvisionerActor.DomainDeleted
import com.convergencelabs.server.db.provision.DomainProvisionerActor.domainTopic
import com.convergencelabs.server.domain.chat.ChatLookupActor
import com.convergencelabs.server.domain.presence.PresenceServiceActor

import akka.actor.ActorRef
import akka.actor.OneForOneStrategy
import akka.actor.Props
import akka.actor.ReceiveTimeout
import akka.actor.Status
import akka.actor.SupervisorStrategy.Resume
import akka.actor.Terminated
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.Subscribe
import akka.cluster.pubsub.DistributedPubSubMediator.SubscribeAck

object DomainActor {
  case class DomainActorChildren(
    modelStoreActor: ActorRef,
    operationStoreActor: ActorRef,
    identityServiceActor: ActorRef,
    presenceServiceActor: ActorRef,
    chatChannelLookupActor: ActorRef)

  def props(
    protocolConfig: ProtocolConfiguration,
    domainPersistenceManager: DomainPersistenceManager,
    receiveTimeout: FiniteDuration): Props = Props(
    new DomainActor(
      protocolConfig,
      domainPersistenceManager,
      receiveTimeout))
}

/**
 * The [[com.convergencelabs.server.domain.DomainActor]] is the supervisor for
 * all actor that comprise the services provided by a particular domain.
 */
class DomainActor(
  private[this] val protocolConfig: ProtocolConfiguration,
  private[this] val domainPersistenceManager: DomainPersistenceManager,
  private[this] val receiveTimeout: FiniteDuration)
  extends ShardedActor(classOf[DomainMessage]) {

  import DomainActor._

  override val supervisorStrategy =
    OneForOneStrategy(maxNrOfRetries = 10, withinTimeRange = 1.minute) {
      case e: Throwable => {
        log.error(e, s"Actor at '${sender.path}' threw error")
        Resume
      }
    }

  private[this] val connectedClients = mutable.Set[ActorRef]()
  private[this] val authenticatedClients = mutable.Map[ActorRef, String]()

  // This is the state that will be set during the initialize method
  private[this] var domainFqn: DomainId = _
  private[this] var persistenceProvider: DomainPersistenceProvider = _
  private[this] var authenticator: AuthenticationHandler = _
  private[this] var children: DomainActorChildren = _

  val mediator = DistributedPubSub(context.system).mediator

  def receiveInitialized: Receive = {
    case message: HandshakeRequest =>
      onHandshakeRequest(message)
    case message: AuthenticationRequest =>
      onAuthenticationRequest(message)
    case message: ClientDisconnected =>
      onClientDisconnect(message)
    case Terminated(client) =>
      handleActorTermination(client)
    case ReceiveTimeout =>
      onReceiveTimeout()
    case SubscribeAck(_) =>
    // no-op
    case DomainDeleted(domainFqn) =>
      domainDeleted()
    case message: DomainStatusRequest =>
      onStatusRequest()
    case message: Any =>
      unhandled(message)
  }

  private[this] def onHandshakeRequest(message: HandshakeRequest): Unit = {
    persistenceProvider.validateConnection() map { _ =>
      this.context.setReceiveTimeout(Duration.Undefined)

      connectedClients.add(message.clientActor)
      context.watch(message.clientActor)

      sender ! HandshakeSuccess(
        this.children.modelStoreActor,
        this.children.operationStoreActor,
        this.children.identityServiceActor,
        this.children.presenceServiceActor,
        this.children.chatChannelLookupActor)
    } recover {
      case cause: Throwable =>
        log.error(cause, s"${identityString}: Could not connect to domain database")
        sender ! Status.Failure(HandshakeFailureException("domain_unavailable", "Could not connect to database."))
    }
  }

  private[this] def onAuthenticationRequest(message: AuthenticationRequest): Unit = {
    log.debug(s"${identityString}: Processing authentication request: ${message.credentials.getClass.getSimpleName}")

    val asker = sender
    val connected = Instant.now()

    // FIXME Remove this try
    try {
      authenticator.authenticate(message.credentials) map {
        case authSuccess @ AuthenticationSuccess(DomainUserSessionId(sessionId, userId), recconectToken) =>
          log.debug(s"${identityString}: Authenticated user successfully, creating session")

          val method = message.credentials match {
            case x: JwtAuthRequest => "jwt"
            case x: ReconnectTokenAuthRequest => "reconnect"
            case x: PasswordAuthRequest => "password"
            case x: AnonymousAuthRequest => "anonymous"
          }

          val session = DomainSession(
            sessionId,
            userId,
            connected,
            None,
            method,
            message.client,
            message.clientVersion,
            message.clientMetaData,
            message.remoteAddress)

          persistenceProvider.sessionStore.createSession(session) map { _ =>
            log.debug(s"${identityString}: Session created replying to ClientActor")
            authenticatedClients.put(message.clientActor, sessionId)
            asker ! authSuccess
          } recover {
            case cause: Throwable =>
              log.error(cause, s"${identityString} Unable to authenticate user because a session could not be created.")
              asker ! AuthenticationFailure
              ()
          }
        case AuthenticationFailure =>
          log.debug(s"${identityString}: AuthenticationFailure")
          asker ! AuthenticationFailure
          ()
      } recover {
        case e: Throwable =>
          log.error(e, s"There was an error authenticating the client")
          asker ! AuthenticationFailure
          ()
      }
    } catch {
      case e: Throwable =>
        log.error(e, s"There was an error authenticating the client")
    }
    
    log.debug(s"${identityString}: Done processing authentication request: ${message.credentials.getClass.getSimpleName}")
  }
  
  private[this] def onStatusRequest(): Unit = {
      sender ! DomainStatusResponse(this.connectedClients.size)
  }

  //
  // Termination and Disconnection
  //

  private[this] def handleActorTermination(actorRef: ActorRef): Unit = {
    if (this.connectedClients.contains(actorRef)) {
      log.debug(s"${identityString}: ClientActor Terminated without cleanly disconnecting. Removing it from the Domain.")
      removeClient(actorRef)
    }
  }

  private[this] def onClientDisconnect(message: ClientDisconnected): Unit = {
    log.debug(s"${identityString}: Recevied ClientDisconnected message, disconnecting client")
    removeClient(message.clientActor)
  }

  private[this] def removeClient(client: ActorRef): Unit = {
    authenticatedClients.remove(client) match {
      case Some(sessionId) =>
        log.debug(s"${identityString}: Disconnecting authenticated client : ${sessionId}")
        persistenceProvider.sessionStore.setSessionDisconneted(sessionId, Instant.now())
      case None =>
        log.debug(s"${identityString}: Disconnecting unathenticated clienmt.")
    }

    connectedClients.remove(client)

    if (connectedClients.isEmpty) {
      log.debug(s"${identityString}: Last client disconnected from domain, setting receive timeout for passivation.")
      this.context.setReceiveTimeout(this.receiveTimeout)
    }
  }

  //
  // Shutdown and passivation
  //

  private[this] def onReceiveTimeout(): Unit = {
    log.debug(s"${identityString}: Receive timeout triggered, passivating")
    this.context.setReceiveTimeout(Duration.Undefined)
    passivate()
  }

  override def passivate(): Unit = {
    super.passivate()
    Option(this.domainFqn).foreach(domainPersistenceManager.releasePersistenceProvider(self, context, _))
  }

  private[this] def domainDeleted(): Unit = {
    log.error(s"${identityString}: Domain deleted, immediately passivating.")
    passivate()
  }

  //
  // Initialization
  //

  override protected def setIdentityData(message: DomainMessage): Try[String] = {
    this.domainFqn = message.domainFqn
    Success(s"${message.domainFqn.namespace}/${message.domainFqn.domainId}")
  }

  override def initialize(msg: DomainMessage): Try[ShardedActorStatUpPlan] = {
    mediator ! Subscribe(domainTopic(domainFqn), self)

    log.debug(s"${identityString}: Aquiring domain persistence provider")
    domainPersistenceManager.acquirePersistenceProvider(self, context, msg.domainFqn) map { provider =>
      log.debug(s"${identityString}: Aquired domain persistence provider")

      this.persistenceProvider = provider
      this.authenticator = new AuthenticationHandler(
        msg.domainFqn,
        provider.configStore,
        provider.jwtAuthKeyStore,
        provider.userStore,
        provider.userGroupStore,
        provider.sessionStore,
        context.dispatcher)

      val identityServiceActor = context.actorOf(IdentityServiceActor.props(domainFqn), IdentityServiceActor.RelativePath)
      val presenceServiceActor = context.actorOf(PresenceServiceActor.props(domainFqn),PresenceServiceActor.RelativePath)
      val chatChannelLookupActor = context.actorOf(ChatLookupActor.props(provider),ChatLookupActor.RelativePath)
      val modelStoreActor = context.actorOf(ModelStoreActor.props(provider), ModelStoreActor.RelativePath)
      val operationStoreActor = context.actorOf(ModelOperationStoreActor.props(provider.modelOperationStore), ModelOperationStoreActor.RelativePath)

      this.children = DomainActorChildren(
        modelStoreActor,
        operationStoreActor,
        identityServiceActor,
        presenceServiceActor,
        chatChannelLookupActor)

      // The idea here is to set the receive timeout in case we don't
      // get a valid handshake. Realistically, the handshake should
      // be the very first message (the one we are initializing with, so
      // this should not happen often.
      this.context.setReceiveTimeout(this.receiveTimeout)

      StartUpRequired
    } recoverWith {
      // This is a special case, we know the domain was not found. In theory this
      // should have been a handshake message, and we want to respond.
      case cause: DomainNotFoundException =>
        msg match {
          case msg: HandshakeRequest =>
            sender ! Status.Failure(HandshakeFailureException(
              "domain_not_found",
              s"The domain '${msg.domainFqn.namespace}/${msg.domainFqn.domainId}' does not exist."))
          case _ =>
            log.warning(s"${identityString}: The domain was not found, but also the first message to the domain was not a handshake, so son't know how to respond.")
        }
        Success(StartUpNotRequired)
      case cause: Throwable =>
        Failure(cause)
    }
  }
}
