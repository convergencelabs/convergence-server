/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is part of the Convergence Server, which is released under
 * the terms of the GNU General Public License version 3 (GPLv3). A copy
 * of the GPLv3 should have been provided along with this file, typically
 * located in the "LICENSE" file, which is part of this source code package.
 * Alternatively, see <https://www.gnu.org/licenses/gpl-3.0.html> for the
 * full text of the GPLv3 license, if it was not provided.
 */

package com.convergencelabs.convergence.server.domain

import java.time.Instant

import akka.actor.SupervisorStrategy.Resume
import akka.actor.{ActorRef, OneForOneStrategy, Props, ReceiveTimeout, Status, Terminated}
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.{Subscribe, SubscribeAck}
import com.convergencelabs.convergence.server.ProtocolConfiguration
import com.convergencelabs.convergence.server.actor.{ShardedActor, ShardedActorStatUpPlan, StartUpNotRequired, StartUpRequired}
import com.convergencelabs.convergence.server.datastore.domain.DomainPersistenceManagerActor.DomainNotFoundException
import com.convergencelabs.convergence.server.datastore.domain._
import com.convergencelabs.convergence.server.db.provision.DomainProvisionerActor.{DomainDeleted, domainTopic}
import com.convergencelabs.convergence.server.domain.chat.ChatManagerActor
import com.convergencelabs.convergence.server.domain.presence.PresenceServiceActor

import scala.collection.mutable
import scala.concurrent.duration.{Duration, DurationInt, FiniteDuration}
import scala.util.{Failure, Success, Try}

object DomainActor {

  case class DomainActorChildren(modelStoreActor: ActorRef,
                                 operationStoreActor: ActorRef,
                                 identityServiceActor: ActorRef,
                                 presenceServiceActor: ActorRef,
                                 chatChannelLookupActor: ActorRef)

  def props(protocolConfig: ProtocolConfiguration,
            domainPersistenceManager: DomainPersistenceManager,
            receiveTimeout: FiniteDuration): Props = Props(
    new DomainActor(
      protocolConfig,
      domainPersistenceManager,
      receiveTimeout))
}

/**
 * The [[DomainActor]] is the supervisor for all actor that comprise the
 * services provided by a particular domain. It is responsible for
 * authenticating users into the domain and handling client connections
 * and disconnections.
 */
class DomainActor(private[this] val protocolConfig: ProtocolConfiguration,
                  private[this] val domainPersistenceManager: DomainPersistenceManager,
                  private[this] val receiveTimeout: FiniteDuration)
  extends ShardedActor(classOf[DomainMessage]) {

  import DomainActor._

  override val supervisorStrategy: OneForOneStrategy =
    OneForOneStrategy(maxNrOfRetries = 10, withinTimeRange = 1.minute) {
      case e: Throwable => {
        log.error(e, s"Actor at '${sender.path}' threw exception")
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

  private[this] val mediator = DistributedPubSub(context.system).mediator

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
    case DomainDeleted(_) =>
      domainDeleted()
    case _: DomainStatusRequest =>
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
        log.error(cause, s"$identityString: Could not connect to domain database")
        sender ! Status.Failure(HandshakeFailureException("domain_unavailable", "Could not connect to database."))
    }
  }

  private[this] def onAuthenticationRequest(message: AuthenticationRequest): Unit = {
    log.debug(s"$identityString: Processing authentication request: ${message.credentials.getClass.getSimpleName}")

    val replyTo = sender
    val connected = Instant.now()

    authenticator.authenticate(message.credentials) map {
      case authSuccess@AuthenticationSuccess(DomainUserSessionId(sessionId, userId), reconnectToken) =>
        log.debug(s"$identityString: Authenticated user successfully, creating session")

        val method = message.credentials match {
          case _: JwtAuthRequest => "jwt"
          case _: ReconnectTokenAuthRequest => "reconnect"
          case _: PasswordAuthRequest => "password"
          case _: AnonymousAuthRequest => "anonymous"
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
          log.debug(s"$identityString: Session created replying to ClientActor")
          authenticatedClients.put(message.clientActor, sessionId)
          replyTo ! authSuccess
        } recover {
          case cause: Throwable =>
            log.error(cause, s"$identityString Unable to authenticate user because a session could not be created.")
            replyTo ! AuthenticationFailure
            ()
        }
      case AuthenticationFailure =>
        log.debug(s"$identityString: AuthenticationFailure")
        replyTo ! AuthenticationFailure
        ()
    } recover {
      case e: Throwable =>
        log.error(e, s"There was an error authenticating the client")
        replyTo ! AuthenticationFailure
        ()
    }

    log.debug(s"$identityString: Done processing authentication request: ${message.credentials.getClass.getSimpleName}")
  }

  private[this] def onStatusRequest(): Unit = {
    sender ! DomainStatusResponse(this.connectedClients.size)
  }

  //
  // Termination and Disconnection
  //

  private[this] def handleActorTermination(actorRef: ActorRef): Unit = {
    if (this.connectedClients.contains(actorRef)) {
      log.debug(s"$identityString: ClientActor Terminated without cleanly disconnecting. Removing it from the Domain.")
      removeClient(actorRef)
    }
  }

  private[this] def onClientDisconnect(message: ClientDisconnected): Unit = {
    log.debug(s"$identityString: Recevied ClientDisconnected message, disconnecting client")
    removeClient(message.clientActor)
  }

  private[this] def removeClient(client: ActorRef): Unit = {
    authenticatedClients.remove(client) match {
      case Some(sessionId) =>
        log.debug(s"$identityString: Disconnecting authenticated client : $sessionId")
        persistenceProvider.sessionStore.setSessionDisconnected(sessionId, Instant.now())
      case None =>
        log.debug(s"$identityString: Disconnecting unauthenticated client.")
    }

    connectedClients.remove(client)

    if (connectedClients.isEmpty) {
      log.debug(s"$identityString: Last client disconnected from domain, setting receive timeout for passivation.")
      this.context.setReceiveTimeout(this.receiveTimeout)
    }
  }

  //
  // Shutdown and passivation
  //

  private[this] def onReceiveTimeout(): Unit = {
    log.debug(s"$identityString: Receive timeout triggered, passivating")
    this.context.setReceiveTimeout(Duration.Undefined)
    passivate()
  }

  override def passivate(): Unit = {
    super.passivate()
    Option(this.domainFqn).foreach(domainPersistenceManager.releasePersistenceProvider(self, context, _))
  }

  private[this] def domainDeleted(): Unit = {
    log.error(s"$identityString: Domain deleted, immediately passivating.")
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

    log.debug(s"$identityString: Acquiring domain persistence provider")
    domainPersistenceManager.acquirePersistenceProvider(self, context, msg.domainFqn) map { provider =>
      log.debug(s"$identityString: Acquired domain persistence provider")

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
      val presenceServiceActor = context.actorOf(PresenceServiceActor.props(domainFqn), PresenceServiceActor.RelativePath)
      val chatChannelLookupActor = context.actorOf(ChatManagerActor.props(provider), ChatManagerActor.RelativePath)
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
      case _: DomainNotFoundException =>
        msg match {
          case msg: HandshakeRequest =>
            sender ! Status.Failure(HandshakeFailureException(
              "domain_not_found",
              s"The domain '${msg.domainFqn.namespace}/${msg.domainFqn.domainId}' does not exist."))
          case _ =>
            log.warning(s"$identityString: The domain was not found, but also the first message to the domain was not a handshake, so son't know how to respond.")
        }
        Success(StartUpNotRequired)
      case cause: Throwable =>
        Failure(cause)
    }
  }
}
