package com.convergencelabs.server.domain

import java.time.Instant

import scala.collection.mutable
import scala.concurrent.duration.Duration
import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.FiniteDuration
import scala.util.Failure
import scala.util.Success
import scala.util.Try
import scala.util.control.NonFatal

import com.convergencelabs.server.ProtocolConfiguration
import com.convergencelabs.server.datastore.domain.DomainPersistenceManager
import com.convergencelabs.server.datastore.domain.DomainPersistenceManagerActor
import com.convergencelabs.server.datastore.domain.DomainPersistenceProvider
import com.convergencelabs.server.datastore.domain.DomainSession
import com.convergencelabs.server.domain.chat.ChatChannelLookupActor
import com.convergencelabs.server.domain.model.ModelLookupActor

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.OneForOneStrategy
import akka.actor.PoisonPill
import akka.actor.Props
import akka.actor.ReceiveTimeout
import akka.actor.Status
import akka.actor.SupervisorStrategy.Resume
import akka.actor.Terminated
import akka.cluster.sharding.ShardRegion.Passivate

object DomainActor {
  case class DomainActorChildren(
    modelQueryManagerActor: ActorRef,
    userServiceActor: ActorRef,
    activityServiceActor: ActorRef,
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
    extends Actor
    with ActorLogging {

  import DomainActor._

  override val supervisorStrategy =
    OneForOneStrategy(maxNrOfRetries = 10, withinTimeRange = 1.minute) {
      case e: Throwable => {
        log.error(e, s"Actor at '${sender.path}' threw error")
        Resume
      }
    }

  private[this] implicit val ec = context.dispatcher
  private[this] val connectedClients = mutable.Set[ActorRef]()
  private[this] val authenticatedClients = mutable.Map[ActorRef, String]()

  private[this] var _domainFqn: Option[DomainFqn] = None
  private[this] var _persistenceProvider: Option[DomainPersistenceProvider] = None
  private[this] var _authenticator: Option[AuthenticationHandler] = None
  private[this] var _children: Option[DomainActorChildren] = None

  this.context.setReceiveTimeout(this.receiveTimeout)

  def receive: Receive = receiveUninitialized

  private[this] def receiveUninitialized: Receive = {
    case msg: DomainMessage =>
      initialize(msg).map(_ => receiveInitialized(msg))
    case unknown: Any =>
      unhandled(unknown)
  }

  def receiveInitialized: Receive = {
    case message: HandshakeRequest =>
      onHandshakeRequest(message)
    case message: AuthenticationRequest =>
      onAuthenticationRequest(message)
    case message: ClientDisconnected =>
      onClientDisconnect(message)
    case Terminated(client) =>
      handleDeathWatch(client)
    case message: Any =>
      unhandled(message)
  }

  private[this] def receivePassivating: Receive = {
    case msg: ReceiveTimeout =>
    // ignore
    case msg: DomainMessage =>
      // Forward this back to the shard region, it will be handled by the next actor that is stood up.
      this.context.parent.forward(msg)
    case msg: Any =>
      unhandled(msg)
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
      this.context.setReceiveTimeout(Duration.Inf)

      connectedClients += message.clientActor
      context.watch(message.clientActor)
      sender ! HandshakeSuccess(
        self,
        this.children.modelQueryManagerActor,
        this.children.userServiceActor,
        this.children.activityServiceActor,
        this.children.presenceServiceActor,
        this.children.chatChannelLookupActor)
    } recover {
      case cause: Exception =>
        log.error(cause, "Could not connect to domain database")
        sender ! Status.Failure(HandshakeFailureException("domain_unavailable", "Could not connect to database."))
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
      context.parent ! Passivate(stopMessage = PoisonPill)
      this.context.become(receivePassivating)
      this.context.setReceiveTimeout(this.receiveTimeout)
    }
  }

  private[this] def initialize(msg: DomainMessage): Try[Unit] = {
    log.debug(s"DomainActor initializing: '{}'", msg.domainFqn)
    domainPersistenceManager.acquirePersistenceProvider(self, context, msg.domainFqn) map { provider =>
      this._persistenceProvider = Some(provider)
      this._domainFqn = Some(msg.domainFqn)
      this._authenticator = Some(new AuthenticationHandler(
        provider.configStore,
        provider.jwtAuthKeyStore,
        provider.userStore,
        provider.userGroupStore,
        provider.sessionStore,
        context.dispatcher))

      val modelQueryManagerActor = context.actorOf(ModelLookupActor.props(
        domainFqn,
        DomainPersistenceManagerActor),
        ModelLookupActor.RelativePath)

      val userServiceActor = context.actorOf(IdentityServiceActor.props(
        domainFqn),
        IdentityServiceActor.RelativePath)

      val activityServiceActor = context.actorOf(ActivityServiceActor.props(
        domainFqn),
        ActivityServiceActor.RelativePath)

      val presenceServiceActor = context.actorOf(PresenceServiceActor.props(
        domainFqn),
        PresenceServiceActor.RelativePath)

      val chatChannelLookupActor = context.actorOf(ChatChannelLookupActor.props(
        domainFqn),
        ChatChannelLookupActor.RelativePath)

      this._children = Some(DomainActorChildren(
        modelQueryManagerActor,
        userServiceActor,
        activityServiceActor,
        presenceServiceActor,
        chatChannelLookupActor))
      context.become(receiveInitialized)
      log.debug(s"DomainActor initialized: {}", domainFqn)
      ()
    } recoverWith {
      case NonFatal(cause) =>
        log.debug(s"Error initializing DomainActor: {}", domainFqn)
        Failure(cause)
    }
  }

  private[this] def persistenceProvider = this._persistenceProvider.getOrElse {
    throw new IllegalStateException("Can not access persistenceProvider before the domain is initialized.")
  }

  private[this] def domainFqn = this._domainFqn.getOrElse {
    throw new IllegalStateException("Can not access domainFqn before the domain is initialized.")
  }

  private[this] def authenticator = this._authenticator.getOrElse {
    throw new IllegalStateException("Can not access authenticator before the domain is initialized.")
  }

  private[this] def children = this._children.getOrElse {
    throw new IllegalStateException("Can not access children before the domain is initialized.")
  }

  override def postStop(): Unit = {
    this._domainFqn.foreach { d =>
      log.debug(s"DomainActor shutting down: {}", d)
      domainPersistenceManager.releasePersistenceProvider(self, context, d)
    }
  }
}


