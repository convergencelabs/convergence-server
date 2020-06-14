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

import akka.actor.typed._
import akka.actor.typed.pubsub.Topic
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import com.convergencelabs.convergence.server.ProtocolConfiguration
import com.convergencelabs.convergence.server.actor._
import com.convergencelabs.convergence.server.api.realtime.ClientActor
import com.convergencelabs.convergence.server.datastore.domain.DomainPersistenceManagerActor.DomainNotFoundException
import com.convergencelabs.convergence.server.datastore.domain._
import com.convergencelabs.convergence.server.db.provision.DomainLifecycleTopic
import com.convergencelabs.convergence.server.domain.chat.ChatManagerActor
import com.convergencelabs.convergence.server.domain.presence.PresenceServiceActor
import grizzled.slf4j.Logging

import scala.collection.mutable
import scala.concurrent.duration.FiniteDuration
import scala.util.{Failure, Success, Try}


/**
 * The [[DomainActor]] is the supervisor for all actor that comprise the
 * services provided by a particular domain. It is responsible for
 * authenticating users into the domain and handling client connections
 * and disconnections.
 */
class DomainActor private(context: ActorContext[DomainActor.Message],
                          shardRegion: ActorRef[DomainActor.Message],
                          shard: ActorRef[ClusterSharding.ShardCommand],
                          protocolConfig: ProtocolConfiguration, // FIXME is this needed?
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

  override def onSignal: PartialFunction[Signal, Behavior[Message]] = super.onSignal orElse {
    case Terminated(client) =>
      handleActorTermination(client.asInstanceOf[ActorRef[ClientActor.Disconnect]])
  }

  override def receiveInitialized(msg: Message): Behavior[Message] = msg match {
    case message: HandshakeRequest =>
      onHandshakeRequest(message)
    case message: AuthenticationRequest =>
      onAuthenticationRequest(message)
    case message: ClientDisconnected =>
      onClientDisconnect(message)
    case DomainDeleted(id) =>
      domainDeleted(id)
    case msg: DomainStatusRequest =>
      onStatusRequest(msg)
    case _: ReceiveTimeout =>
      onReceiveTimeout()
  }

  private[this] def onHandshakeRequest(message: HandshakeRequest): Behavior[Message] = {
    persistenceProvider.validateConnection()
      .map { _ =>
        context.cancelReceiveTimeout()
        context.watch(message.clientActor)

        connectedClients.add(message.clientActor)

        val success = HandshakeSuccess(
          this.children.modelStoreActor,
          this.children.operationStoreActor,
          this.children.identityServiceActor,
          this.children.presenceServiceActor,
          this.children.chatManagerActor)

        HandshakeResponse(Right(success))
      }
      .recover { cause: Throwable =>
        error(s"$identityString: Could not connect to domain database", cause)
        val failure = DomainDatabaseError(message.domainId)
        HandshakeResponse(Left(failure))
      }
      .foreach(message.replyTo ! _)

    Behaviors.same
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

    authenticator
      .authenticate(credentials)
      .map { case authSuccess@AuthenticationSuccess(DomainUserSessionId(sessionId, userId), _) =>
        debug(s"$identityString: Authenticated user successfully, creating session")

        val session = DomainSession(
          sessionId, userId, connected, None, method, client, clientVersion, clientMetaData, remoteAddress)

        persistenceProvider
          .sessionStore
          .createSession(session)
          .map { _ =>
            debug(s"$identityString: Session created replying to ClientActor")
            authenticatedClients.put(clientActor, sessionId)
            AuthenticationResponse(Right(authSuccess))
          }
          .recoverWith {
            case cause: Throwable =>
              error(s"$identityString Unable to authenticate user because a session could not be created.", cause)
              Failure(cause)
          }
          .getOrElse(AuthenticationResponse(Left(())))
      }
      .foreach(replyTo ! _)

    debug(s"$identityString: Done processing authentication request: ${message.credentials.getClass.getSimpleName}")

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

  private[this] def domainDeleted(domainId: DomainId): Behavior[Message] = {
    if (this.domainId == domainId) {
      error(s"$identityString: Domain deleted, immediately passivating.")
      this.connectedClients.foreach(_ ! ClientActor.Disconnect())
      this.authenticatedClients.foreach { case (k, _) => k ! ClientActor.Disconnect() }
      passivate()
    } else {
      Behaviors.same
    }
  }

  //
  // Initialization
  //

  override protected def setIdentityData(message: Message): Try[String] = {
    this.domainId = message.domainId
    Success(s"${message.domainId.namespace}/${message.domainId.domainId}")
  }

  override def initialize(msg: Message): Try[ShardedActorStatUpPlan] = {
    domainLifecycleTopic ! Topic.Subscribe(context.messageAdapter[DomainLifecycleTopic.Message] {
      case DomainLifecycleTopic.DomainDeleted(id) =>
        DomainDeleted(id)
    })

    debug(s"$identityString: Acquiring domain persistence provider")
    domainPersistenceManager.acquirePersistenceProvider(context.self, context.system, msg.domainId) map { provider =>
      debug( s"$identityString: Acquired domain persistence provider")

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
    } recoverWith {
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
            protocolConfig: ProtocolConfiguration,
            domainPersistenceManager: DomainPersistenceManager,
            receiveTimeout: FiniteDuration,
            domainLifecycleTopic: ActorRef[DomainLifecycleTopic.TopicMessage]): Behavior[Message] = Behaviors.setup { context =>
    new DomainActor(
      context,
      shardRegion,
      shard,
      protocolConfig,
      domainPersistenceManager,
      receiveTimeout,
      domainLifecycleTopic)
  }

  // TODO evaluate a better supervision strategy.
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

  final case class HandshakeRequest(domainId: DomainId,
                                    clientActor: ActorRef[ClientActor.Disconnect],
                                    reconnect: Boolean,
                                    reconnectToken: Option[String],
                                    replyTo: ActorRef[HandshakeResponse]) extends Message


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

  final case class AuthenticationResponse(response: Either[Unit, AuthenticationSuccess]) extends CborSerializable

  final case class AuthenticationSuccess(session: DomainUserSessionId, reconnectToken: Option[String])

  final case class ClientDisconnected(domainId: DomainId, clientActor: ActorRef[ClientActor.Disconnect]) extends Message

  final case class DomainStatusRequest(domainId: DomainId, replyTo: ActorRef[DomainStatusResponse]) extends Message

  final case class DomainStatusResponse(connectedClients: Int)

  private final case class ReceiveTimeout(domainId: DomainId) extends Message

  final case class DomainDeleted(domainId: DomainId) extends Message


  //
  // Supporting Classes
  //

  final case class DomainActorChildren(modelStoreActor: ActorRef[ModelStoreActor.Message],
                                       operationStoreActor: ActorRef[ModelOperationStoreActor.Message],
                                       identityServiceActor: ActorRef[IdentityServiceActor.Message],
                                       presenceServiceActor: ActorRef[PresenceServiceActor.Message],
                                       chatManagerActor: ActorRef[ChatManagerActor.Message])

}
