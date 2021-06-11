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
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, TimerScheduler}
import akka.actor.typed.{ActorRef, _}
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.util.Timeout
import com.convergencelabs.convergence.server.api.realtime.ClientActor
import com.convergencelabs.convergence.server.backend.services.domain.DomainPersistenceManagerActor.DomainNotFoundException
import com.convergencelabs.convergence.server.backend.services.server.DomainLifecycleTopic
import com.convergencelabs.convergence.server.model.DomainId
import com.convergencelabs.convergence.server.model.domain.session.{DomainSession, DomainSessionAndUserId}
import com.convergencelabs.convergence.server.model.server.domain.{DomainAvailability, DomainStatus}
import com.convergencelabs.convergence.server.util.serialization.akka.CborSerializable
import com.fasterxml.jackson.annotation.{JsonSubTypes, JsonTypeInfo}
import grizzled.slf4j.Logging

import java.time.{Duration, Instant}
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
private class DomainActor(domainId: DomainId,
                          context: ActorContext[DomainActor.Message],
                          timers: TimerScheduler[DomainActor.Message],
                          shardRegion: ActorRef[DomainActor.Message],
                          shard: ActorRef[ClusterSharding.ShardCommand],
                          domainPersistenceManager: DomainPersistenceManager,
                          receiveTimeout: FiniteDuration,
                          domainLifecycleTopic: ActorRef[DomainLifecycleTopic.TopicMessage])
  extends BaseDomainShardedActor[DomainActor.Message](domainId, context, shardRegion, shard, domainPersistenceManager, receiveTimeout: FiniteDuration,
  ) with Logging {

  import DomainActor._

  private[this] val connectedClients = mutable.Set[ActorRef[ClientActor.Disconnect]]()
  private[this] val authenticatedClients = mutable.Map[ActorRef[ClientActor.Disconnect], String]()
  private[this] val lastClientHeartbeat = scala.collection.mutable.Map[String, Instant]()

  // This is the state that will be set during the initialize method
  private[this] var authenticator: AuthenticationHandler = _
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
    case msg: ClientHeartbeat =>
      onClientHeartbeat(msg)
    case InternalDomainStatusChanged(id, status) =>
      domainStatusChanged(id, status)
    case InternalDomainAvailabilityChanged(id, availability) =>
      domainAvailabilityChanged(id, availability)
    case msg: DomainStatusRequest =>
      onStatusRequest(msg)
    case _: ReceiveTimeout =>
      onReceiveTimeout()
    case _: CheckSessions =>
      onCheckSessions()
    case msg: ShutdownRequest =>
      this.handleShutdownRequest(msg)
  }

  private[this] def handleSignal: PartialFunction[Signal, Behavior[Message]] = {
    case Terminated(client) =>
      handleActorTermination(client.asInstanceOf[ActorRef[ClientActor.Disconnect]])
  }

  override def postStop(): Unit = {
    super.postStop()
    shutdownTask.cancel()
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
        this.disableReceiveTimeout()
        context.watch(clientActor)
        connectedClients.add(clientActor)
        val success = HandshakeSuccess()
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

  private[this] def onClientHeartbeat(message: ClientHeartbeat): Behavior[Message] = {
    this.lastClientHeartbeat.addOne(message.sessionId -> Instant.now())
    Behaviors.same
  }

  private[this] def removeClient(client: ActorRef[ClientActor.Disconnect]): Behavior[Message] = {
    authenticatedClients.remove(client) match {
      case Some(sessionId) =>
        debug(s"$identityString: Disconnecting authenticated client : $sessionId")
        persistenceProvider.sessionStore.setSessionDisconnected(sessionId, Instant.now())
        lastClientHeartbeat.remove(sessionId)
      case None =>
        debug(s"$identityString: Disconnecting unauthenticated client.")
    }

    connectedClients.remove(client)

    if (connectedClients.isEmpty) {
      debug(s"$identityString: Last client disconnected from domain, setting receive timeout for passivation.")
      this.enableReceiveTimeout()
    }

    Behaviors.same
  }

  private[this] def onCheckSessions(): Behavior[Message] = {
    val now = Instant.now()
    val max = Duration.ofSeconds(60)
    val expiredSessions = lastClientHeartbeat.filter { case (_, lastSeen) =>
      val d = Duration.between(lastSeen, now)
      d.compareTo(max) > 1
    }.keySet.toSet

    this.persistenceProvider.sessionStore.setSessionsDisconnected(expiredSessions, now).recover {
      case t: Throwable =>
        error("Error marking dead sessions as disconnected", t)
    }.foreach { _ =>
      expiredSessions.foreach(lastClientHeartbeat.remove)
    }

    Behaviors.same
  }

  //
  // Shutdown and passivation
  //

  private[this] def onReceiveTimeout(): Behavior[Message] = {
    debug(s"$identityString: Receive timeout triggered, passivating")
    this.disableReceiveTimeout()
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

  override def initializeState(msg: Message): Try[Unit] = {
    this.persistenceProvider.domainStateProvider
      .getDomainState()
      .map {
        case Some(state) =>
          this.status = state.status
          this.availability = state.availability

          domainLifecycleTopic ! Topic.Subscribe(context.messageAdapter[DomainLifecycleTopic.Message] {
            case DomainLifecycleTopic.DomainStatusChanged(id, status) =>
              InternalDomainStatusChanged(id, status)
            case DomainLifecycleTopic.DomainAvailabilityChanged(id, availability) =>
              InternalDomainAvailabilityChanged(id, availability)
          })

          this.authenticator = new AuthenticationHandler(
            msg.domainId,
            persistenceProvider.configStore,
            persistenceProvider.jwtAuthKeyStore,
            persistenceProvider.userStore,
            persistenceProvider.userGroupStore,
            persistenceProvider.sessionStore,
            context.executionContext)

        case None =>
          Failure(DomainNotFoundException(domainId))
      }
      .map(_ => {
        this.persistenceProvider.sessionStore.getConnectedSessions().map { sessions =>
          val now = Instant.now()
          sessions.foreach(id => {
            this.lastClientHeartbeat += (id -> now)
          })
          val msg = CheckSessions(this.domainId)
          timers.startTimerAtFixedRate(msg, FiniteDuration(20, TimeUnit.SECONDS))
        }
      })
  }

  override protected def handleDomainNotFound(msg: Message): Unit = {
    msg match {
      case msg: HandshakeRequest =>
        msg.replyTo ! HandshakeResponse(Left(DomainNotFound(msg.domainId)))
      case _ =>
        warn(s"$identityString: The domain was not found, but also the first message to the domain was not a handshake, so son't know how to respond.")
    }
  }

  override protected def getDomainId(msg: Message): DomainId = msg.domainId

  override protected def getReceiveTimeoutMessage(): Message = ReceiveTimeout(this.domainId)
}


object DomainActor {

  def apply(domainId: DomainId,
            shardRegion: ActorRef[Message],
            shard: ActorRef[ClusterSharding.ShardCommand],
            domainPersistenceManager: DomainPersistenceManager,
            receiveTimeout: FiniteDuration,
            domainLifecycleTopic: ActorRef[DomainLifecycleTopic.TopicMessage]): Behavior[Message] =
    Behaviors.setup { context =>
      Behaviors.withTimers { timers =>
        new DomainActor(
          domainId,
          context,
          timers,
          shardRegion,
          shard,
          domainPersistenceManager,
          receiveTimeout,
          domainLifecycleTopic)
      }
    }

  /////////////////////////////////////////////////////////////////////////////
  // Message Protocol
  /////////////////////////////////////////////////////////////////////////////

  sealed trait Message extends CborSerializable {
    val domainId: DomainId
  }

  private final case class ShutdownRequest(domainId: DomainId, replyTo: ActorRef[Done]) extends Message

  private final case class ReceiveTimeout(domainId: DomainId) extends Message

  private final case class CheckSessions(domainId: DomainId) extends Message

  final case class HandshakeRequest(domainId: DomainId,
                                    clientActor: ActorRef[ClientActor.Disconnect],
                                    reconnect: Boolean,
                                    reconnectToken: Option[String],
                                    replyTo: ActorRef[HandshakeResponse]) extends Message

  final case class ClientHeartbeat(domainId: DomainId, sessionId: String) extends Message


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

  final case class HandshakeSuccess()


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
}
