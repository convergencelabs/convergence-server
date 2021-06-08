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

package com.convergencelabs.convergence.server.backend.services.domain

import akka.Done
import akka.actor.CoordinatedShutdown
import akka.actor.typed.pubsub.Topic
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, _}
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.util.Timeout
import com.convergencelabs.convergence.server.api.realtime.ClientActor
import com.convergencelabs.convergence.server.backend.datastore.domain._
import com.convergencelabs.convergence.server.backend.services.domain.DomainPersistenceManagerActor.DomainNotFoundException
import com.convergencelabs.convergence.server.backend.services.domain.chat.ChatManagerActor
import com.convergencelabs.convergence.server.backend.services.domain.model.{ModelOperationStoreActor, ModelStoreActor}
import com.convergencelabs.convergence.server.backend.services.domain.presence.PresenceServiceActor
import com.convergencelabs.convergence.server.backend.services.server.DomainLifecycleTopic
import com.convergencelabs.convergence.server.model.DomainId
import com.convergencelabs.convergence.server.model.domain.session.{DomainSession, DomainSessionAndUserId}
import com.convergencelabs.convergence.server.model.server.domain.{DomainAvailability, DomainStatus}
import com.convergencelabs.convergence.server.util.actor._
import com.convergencelabs.convergence.server.util.serialization.akka.CborSerializable
import com.fasterxml.jackson.annotation.{JsonSubTypes, JsonTypeInfo}
import grizzled.slf4j.Logging

import java.time.Instant
import java.util.concurrent.TimeUnit
import scala.collection.mutable
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.util.{Failure, Success, Try}


/**
 * The [[DomainActor]] is the supervisor for all actor that comprise the
 * services provided by a particular domain. It is responsible for
 * authenticating users into the domain and handling client connections
 * and disconnections.
 */
private class DomainActor(context: ActorContext[DomainActor.Message],
                          shardRegion: ActorRef[DomainActor.Message],
                          shard: ActorRef[ClusterSharding.ShardCommand],
                          domainPersistenceManager: DomainPersistenceManager,
                          receiveTimeout: FiniteDuration,
                          domainLifecycleTopic: ActorRef[DomainLifecycleTopic.TopicMessage])
  extends ShardedActor[DomainActor.Message](context, shardRegion, shard) with Logging {

  import DomainActor._

  private[this] val connectedClients = mutable.Set[ActorRef[ClientActor.Disconnect]]()
  private[this] val authenticatedClients = mutable.Map[ActorRef[ClientActor.Disconnect], String]()

  // This is the state that will be set during the initialize method
  private[this] var domainId: DomainId = _
  private[this] var persistenceProvider: DomainPersistenceProvider = _
  private[this] var authenticator: AuthenticationHandler = _
  private[this] var children: DomainActorChildren = _
  private[this] var status: DomainStatus.Value = _
  private[this] var availability: DomainAvailability.Value = _

  private[this] val shutdownTask = {
    val self = context.self
    implicit val scheduler: Scheduler = context.system.scheduler
    CoordinatedShutdown(context.system).addCancellableTask(CoordinatedShutdown.PhaseBeforeClusterShutdown, "Domain Actor Shutdown") { () =>
      implicit val t: Timeout = Timeout(5, TimeUnit.SECONDS)
      self.ask[Done](r => ShutdownRequest(this.domainId, r))
      Future.successful(Done)
    }
  }

  override def onSignal: PartialFunction[Signal, Behavior[Message]] = handleSignal orElse super.onSignal

  override def receiveInitialized(msg: Message): Behavior[Message] = msg match {
    case message: HandshakeRequest =>
      onHandshakeRequest(message)
    case message: AuthenticationRequest =>
      onAuthenticationRequest(message)
    case message: ClientDisconnected =>
      onClientDisconnect(message)
    case InternalDomainStatusChanged(id, status) =>
      domainStatusChanged(id, status)
    case InternalDomainAvailabilityChanged(id, availability) =>
      domainAvailabilityChanged(id, availability)
    case msg: DomainStatusRequest =>
      onStatusRequest(msg)
    case _: ReceiveTimeout =>
      onReceiveTimeout()
    case msg: ShutdownRequest =>
      this.handleShutdownRequest(msg)
  }

  private[this] def handleSignal: PartialFunction[Signal, Behavior[Message]] = {
    case Terminated(client) =>
      handleActorTermination(client.asInstanceOf[ActorRef[ClientActor.Disconnect]])
    case PostStop =>
      debug("DomainActor Stopped: " + this.domainId)
      shutdownTask.cancel()
      Behaviors.same
  }

  def handleShutdownRequest(msg: ShutdownRequest): Behavior[DomainActor.Message] = {
    this.connectedClients.foreach(c => removeClient(c))
    msg.replyTo ! Done
    Behaviors.same
  }

  private[this] def onHandshakeRequest(message: HandshakeRequest): Behavior[Message] = {
    this.persistenceProvider.domainStateProvider.getDomainState()
      .flatMap {
        case None =>
          Success(Left(DomainNotFound(message.domainId)))
        case Some(_) =>
          processHandshakeForExistingDomain(message.clientActor)
      }
      .recover { cause: Throwable =>
        error(s"$identityString: Could not connect to domain database", cause)
        val failure = DomainDatabaseError(message.domainId)
        Left(failure)
      }
      .foreach(message.replyTo ! HandshakeResponse(_))

    Behaviors.same
  }

  private[this] def processHandshakeForExistingDomain(clientActor: ActorRef[ClientActor.Disconnect]): Try[Either[HandshakeError, HandshakeSuccess]] = {
    this.status match {
      case DomainStatus.Ready =>
        this.availability match {
          case DomainAvailability.Offline =>
            Success(Left(DomainNotFound(this.domainId)))
          case _ =>
            completeHandshake(clientActor)
        }
      case _ =>
        if (this.availability == DomainAvailability.Offline) {
          Success(Left(DomainNotFound(this.domainId)))
        } else {
          Success(Left(DomainUnavailable(this.domainId)))
        }
    }
  }

  private[this] def completeHandshake(clientActor: ActorRef[ClientActor.Disconnect]): Try[Either[HandshakeError, HandshakeSuccess]] = {
    persistenceProvider.validateConnection()
      .map { _ =>
        context.cancelReceiveTimeout()
        context.watch(clientActor)

        connectedClients.add(clientActor)

        val success = HandshakeSuccess(
          this.children.modelStoreActor,
          this.children.operationStoreActor,
          this.children.identityServiceActor,
          this.children.presenceServiceActor,
          this.children.chatManagerActor)
        Right(success)
      }
  }

  private[this] def onAuthenticationRequest(message: AuthenticationRequest): Behavior[Message] = {
    debug(s"$identityString: Processing authentication request: ${message.credentials.getClass.getSimpleName}")

    val AuthenticationRequest(
    _, clientActor, remoteAddress, client, clientVersion, clientMetaData, credentials, replyTo) = message

    val connected = Instant.now()

    val method = message.credentials match {
      case _: JwtAuthRequest => "jwt"
      case _: ReconnectTokenAuthRequest => "reconnect"
      case _: PasswordAuthRequest => "password"
      case _: AnonymousAuthRequest => "anonymous"
    }

    val response = authenticator
      .authenticate(credentials, this.availability)
      .fold(
        { msg =>
          debug(s"$identityString: Authentication failed")
          AuthenticationResponse(Left(AuthenticationFailed(msg)))
        },
        {
          case authSuccess@AuthenticationSuccess(DomainSessionAndUserId(sessionId, userId), _) =>
            debug(s"$identityString: Authenticated user successfully, creating session")
            val session = DomainSession(
              sessionId, userId, connected, None, method, client, clientVersion, clientMetaData, remoteAddress)

            persistenceProvider
              .sessionStore
              .createSession(session)
              .map { _ =>
                debug(s"$identityString: Session created, replying to ClientActor")
                authenticatedClients.put(clientActor, sessionId)
                AuthenticationResponse(Right(authSuccess))
              }
              .recoverWith { cause =>
                error(s"$identityString Unable to authenticate user because a session could not be created.", cause)
                Failure(cause)
              }
              .getOrElse(AuthenticationResponse(Left(AuthenticationFailed(None))))
        }
      )

    debug(s"$identityString: Done processing authentication request: ${message.credentials.getClass.getSimpleName}")
    replyTo ! response

    Behaviors.same
  }

  private[this] def onStatusRequest(msg: DomainStatusRequest): Behavior[Message] = {
    msg.replyTo ! DomainStatusResponse(this.connectedClients.size)
    Behaviors.same
  }

  //
  // Termination and Disconnection
  //

  private[this] def handleActorTermination(actorRef: ActorRef[ClientActor.Disconnect]): Behavior[Message] = {
    if (this.connectedClients.contains(actorRef)) {
      debug(s"$identityString: ClientActor Terminated without cleanly disconnecting. Removing it from the Domain.")
      removeClient(actorRef)
    } else {
      Behaviors.same
    }
  }

  private[this] def onClientDisconnect(message: ClientDisconnected): Behavior[Message] = {
    debug(s"$identityString: Received ClientDisconnected message, disconnecting client")
    removeClient(message.clientActor)
  }

  private[this] def removeClient(client: ActorRef[ClientActor.Disconnect]): Behavior[Message] = {
    authenticatedClients.remove(client) match {
      case Some(sessionId) =>
        debug(s"$identityString: Disconnecting authenticated client : $sessionId")
        persistenceProvider.sessionStore.setSessionDisconnected(sessionId, Instant.now())
      case None =>
        debug(s"$identityString: Disconnecting unauthenticated client.")
    }

    connectedClients.remove(client)

    if (connectedClients.isEmpty) {
      debug(s"$identityString: Last client disconnected from domain, setting receive timeout for passivation.")
      this.context.setReceiveTimeout(this.receiveTimeout, ReceiveTimeout(this.domainId))
    }

    Behaviors.same
  }

  //
  // Shutdown and passivation
  //

  private[this] def onReceiveTimeout(): Behavior[Message] = {
    debug(s"$identityString: Receive timeout triggered, passivating")
    this.context.cancelReceiveTimeout()
    passivate()
  }

  override def passivate(): Behavior[Message] = {
    Option(this.domainId).foreach(domainPersistenceManager.releasePersistenceProvider(context.self, context.system, _))
    super.passivate()
  }

  private[this] def domainStatusChanged(domainId: DomainId, status: DomainStatus.Value): Behavior[Message] = {
    if (this.domainId == domainId) {
      this.status = status
      this.processAvailabilityAndStatus()
    } else {
      Behaviors.same
    }
  }

  private[this] def domainAvailabilityChanged(domainId: DomainId, availability: DomainAvailability.Value): Behavior[Message] = {
    if (this.domainId == domainId) {
      this.availability = availability
      this.processAvailabilityAndStatus()
    } else {
      Behaviors.same
    }
  }

  private[this] def processAvailabilityAndStatus(): Behavior[Message] = {
    this.availability match {
      case DomainAvailability.Offline =>
        debug(s"$identityString: Domain going offline, immediately passivating.")
        disconnectAndPassivate()
      case DomainAvailability.Maintenance =>
        debug(s"$identityString: Domain in maintenance mode, immediately passivating.")
        disconnectAndPassivate()
      case DomainAvailability.Online =>
        this.status match {
          case DomainStatus.Error =>
            debug(s"$identityString: Domain in error state, immediately passivating.")
            disconnectAndPassivate()
          case DomainStatus.Deleting =>
            debug(s"$identityString: Domain deleting, immediately passivating.")
            disconnectAndPassivate()
          case DomainStatus.SchemaUpgradeRequired =>
            debug(s"$identityString: Domain needs upgrading, immediately passivating.")
            disconnectAndPassivate()
          case DomainStatus.SchemaUpgrading =>
            debug(s"$identityString: Domain upgrading, immediately passivating.")
            disconnectAndPassivate()
          case DomainStatus.Ready =>
            Behaviors.same
        }
    }
  }

  private[this] def disconnectAndPassivate(): Behavior[Message] = {
    this.connectedClients.foreach(_ ! ClientActor.Disconnect())
    this.authenticatedClients.foreach { case (k, _) => k ! ClientActor.Disconnect() }
    passivate()
  }

  //
  // Initialization
  //

  override protected def setIdentityData(message: Message): Try[String] = {
    this.domainId = message.domainId
    Success(s"${message.domainId.namespace}/${message.domainId.domainId}")
  }

  override def initialize(msg: Message): Try[ShardedActorStatUpPlan] = {
    (for {
      provider <- domainPersistenceManager.acquirePersistenceProvider(context.self, context.system, msg.domainId)
      domainState <- provider.domainStateProvider.getDomainState() flatMap {
        case Some(state) => Success(state)
        case None => Failure(DomainNotFoundException(domainId))
      }
    } yield {
      this.status = domainState.status
      this.availability = domainState.availability

      domainLifecycleTopic ! Topic.Subscribe(context.messageAdapter[DomainLifecycleTopic.Message] {
        case DomainLifecycleTopic.DomainStatusChanged(id, status) =>
          InternalDomainStatusChanged(id, status)
        case DomainLifecycleTopic.DomainAvailabilityChanged(id, availability) =>
          InternalDomainAvailabilityChanged(id, availability)
      })

      this.persistenceProvider = provider
      this.authenticator = new AuthenticationHandler(
        msg.domainId,
        provider.configStore,
        provider.jwtAuthKeyStore,
        provider.userStore,
        provider.userGroupStore,
        provider.sessionStore,
        context.executionContext)

      val identityServiceActor = context.spawn(supervise(IdentityServiceActor(persistenceProvider)), "IdentityService")
      val presenceServiceActor = context.spawn(supervise(PresenceServiceActor()), "PresenceService")
      val chatManagerActor = context.spawn(supervise(ChatManagerActor(provider.chatStore, provider.permissionsStore)), "ChatManager")
      val modelStoreActor = context.spawn(supervise(ModelStoreActor(persistenceProvider)), "ModelStore")
      val operationStoreActor = context.spawn(supervise(ModelOperationStoreActor(persistenceProvider.modelOperationStore)), "ModelOperationStore")

      this.children = DomainActorChildren(
        modelStoreActor,
        operationStoreActor,
        identityServiceActor,
        presenceServiceActor,
        chatManagerActor)

      // The idea here is to set the receive timeout in case we don't
      // get a valid handshake. Realistically, the handshake should
      // be the very first message (the one we are initializing with, so
      // this should not happen often.
      this.context.setReceiveTimeout(this.receiveTimeout, ReceiveTimeout(domainId))

      StartUpRequired
    })
      .recoverWith {
        // This is a special case, we know the domain was not found. In theory this
        // should have been a handshake message, and we want to respond.
        case _: DomainNotFoundException =>
          msg match {
            case msg: HandshakeRequest =>
              msg.replyTo ! HandshakeResponse(Left(DomainNotFound(msg.domainId)))
            case _ =>
              warn(s"$identityString: The domain was not found, but also the first message to the domain was not a handshake, so son't know how to respond.")
          }
          Success(StartUpNotRequired)
        case cause: Throwable =>
          Failure(cause)
      }
  }
}


object DomainActor {

  def apply(shardRegion: ActorRef[Message],
            shard: ActorRef[ClusterSharding.ShardCommand],
            domainPersistenceManager: DomainPersistenceManager,
            receiveTimeout: FiniteDuration,
            domainLifecycleTopic: ActorRef[DomainLifecycleTopic.TopicMessage]): Behavior[Message] = Behaviors.setup { context =>
    new DomainActor(
      context,
      shardRegion,
      shard,
      domainPersistenceManager,
      receiveTimeout,
      domainLifecycleTopic)
  }

  private[this] val ChildSupervisionStrategy = SupervisorStrategy.resume

  private def supervise[T](child: Behavior[T]): Behavior[T] = {
    Behaviors
      .supervise(child)
      .onFailure[Throwable](ChildSupervisionStrategy)
  }

  /////////////////////////////////////////////////////////////////////////////
  // Message Protocol
  /////////////////////////////////////////////////////////////////////////////

  sealed trait Message extends CborSerializable {
    val domainId: DomainId
  }

  private final case class ShutdownRequest(domainId: DomainId, replyTo: ActorRef[Done]) extends Message

  private final case class ReceiveTimeout(domainId: DomainId) extends Message

  final case class HandshakeRequest(domainId: DomainId,
                                    clientActor: ActorRef[ClientActor.Disconnect],
                                    reconnect: Boolean,
                                    reconnectToken: Option[String],
                                    replyTo: ActorRef[HandshakeResponse]) extends Message


  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[DomainNotFound], name = "not_found"),
    new JsonSubTypes.Type(value = classOf[DomainDatabaseError], name = "database_error"),
    new JsonSubTypes.Type(value = classOf[DomainUnavailable], name = "unavailable")
  ))
  sealed trait HandshakeError

  final case class DomainNotFound(domainId: DomainId) extends HandshakeError

  final case class DomainDatabaseError(domainId: DomainId) extends HandshakeError

  final case class DomainUnavailable(domainId: DomainId) extends HandshakeError

  final case class HandshakeResponse(handshake: Either[HandshakeError, HandshakeSuccess]) extends CborSerializable

  final case class HandshakeSuccess(modelStoreActor: ActorRef[ModelStoreActor.Message],
                                    operationStoreActor: ActorRef[ModelOperationStoreActor.Message],
                                    identityServiceActor: ActorRef[IdentityServiceActor.Message],
                                    presenceService: ActorRef[PresenceServiceActor.Message],
                                    chatManagerActor: ActorRef[ChatManagerActor.Message])


  final case class AuthenticationRequest(domainId: DomainId,
                                         clientActor: ActorRef[ClientActor.Disconnect],
                                         remoteAddress: String,
                                         client: String,
                                         clientVersion: String,
                                         clientMetaData: String,
                                         credentials: AuthenticationCredentials,
                                         replyTo: ActorRef[AuthenticationResponse]) extends Message

  final case class AuthenticationFailed(msg: Option[String])

  final case class AuthenticationResponse(response: Either[AuthenticationFailed, AuthenticationSuccess]) extends CborSerializable

  final case class AuthenticationSuccess(session: DomainSessionAndUserId, reconnectToken: Option[String])

  final case class ClientDisconnected(domainId: DomainId, clientActor: ActorRef[ClientActor.Disconnect]) extends Message

  final case class DomainStatusRequest(domainId: DomainId, replyTo: ActorRef[DomainStatusResponse]) extends Message

  final case class DomainStatusResponse(connectedClients: Int)

  final case class InternalDomainStatusChanged(domainId: DomainId,
                                               status: DomainStatus.Value) extends Message

  final case class InternalDomainAvailabilityChanged(domainId: DomainId, availability: DomainAvailability.Value) extends Message

  //
  // Supporting Classes
  //

  final case class DomainActorChildren(modelStoreActor: ActorRef[ModelStoreActor.Message],
                                       operationStoreActor: ActorRef[ModelOperationStoreActor.Message],
                                       identityServiceActor: ActorRef[IdentityServiceActor.Message],
                                       presenceServiceActor: ActorRef[PresenceServiceActor.Message],
                                       chatManagerActor: ActorRef[ChatManagerActor.Message])

}
