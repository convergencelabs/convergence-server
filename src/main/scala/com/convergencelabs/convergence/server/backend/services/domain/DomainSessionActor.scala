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

import akka.actor.typed.pubsub.Topic
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, TimerScheduler}
import akka.actor.typed.{ActorRef, _}
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
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
import scala.concurrent.duration.FiniteDuration
import scala.util.{Failure, Success, Try}


/**
 * The [[DomainSessionActor]] is the manages the session corresponding
 * to connected clients. It ensures that sessions for clients that
 * disconnect uncleanly are eventually cleaned up.
 */
private class DomainSessionActor(domainId: DomainId,
                                 context: ActorContext[DomainSessionActor.Message],
                                 timers: TimerScheduler[DomainSessionActor.Message],
                                 shardRegion: ActorRef[DomainSessionActor.Message],
                                 shard: ActorRef[ClusterSharding.ShardCommand],
                                 domainPersistenceManager: DomainPersistenceManager,
                                 receiveTimeout: FiniteDuration,
                                 domainLifecycleTopic: ActorRef[DomainLifecycleTopic.TopicMessage])
  extends BaseDomainShardedActor[DomainSessionActor.Message](domainId, context, shardRegion, shard, domainPersistenceManager, receiveTimeout: FiniteDuration,
  ) with Logging {

  import DomainSessionActor._

  private[this] val activeSessions = scala.collection.mutable.Map[String, Instant]()
  private[this] val connectedClients = scala.collection.mutable.Map[ActorRef[ClientActor.Disconnect], String]()

  private[this] val IdleSessionTimeout = context.system.settings.config.getDuration(
    "convergence.realtime.domain.idle-session-timeout")

  // This is the state that will be set during the initialize method
  private[this] var authenticator: AuthenticationHandler = _
  private[this] var status: DomainStatus.Value = _
  private[this] var availability: DomainAvailability.Value = _

  override def receiveInitialized(msg: Message): Behavior[Message] = msg match {
    case message: ConnectionRequest =>
      onAuthenticationRequest(message)
    case message: ClientDisconnected =>
      onClientDisconnect(message)
    case msg: ClientHeartbeat =>
      onClientHeartbeat(msg)
    case InternalDomainStatusChanged(id, status) =>
      domainStatusChanged(id, status)
    case InternalDomainAvailabilityChanged(id, availability) =>
      domainAvailabilityChanged(id, availability)
    case _: ReceiveTimeout =>
      onReceiveTimeout()
    case _: CheckSessions =>
      onCheckSessions()
  }

  override protected def onTerminated(client: ActorRef[Nothing]): Behavior[Message] = {
    handleActorTermination(client.asInstanceOf[ActorRef[ClientActor.Disconnect]])
  }

  private[this] def onAuthenticationRequest(message: ConnectionRequest): Behavior[Message] = {
    this.persistenceProvider.domainStateProvider.getDomainState()
      .flatMap {
        case None =>
          Success(Left(DomainNotFound(message.domainId)))
        case Some(_) =>
          Success(processAuthenticationForExistingDomain(message))
      }
      .recover { cause: Throwable =>
        error(s"$identityString: Could not connect to domain database", cause)
        val failure = DomainDatabaseError(message.domainId)
        Left(failure)
      }
      .foreach(message.replyTo ! ConnectionResponse(_))

    Behaviors.same
  }

  private[this] def processAuthenticationForExistingDomain(message: ConnectionRequest): Either[ConnectionError, ConnectionSuccess] = {
    this.status match {
      case DomainStatus.Ready =>
        this.availability match {
          case DomainAvailability.Offline =>
            Left(DomainNotFound(this.domainId))
          case _ =>
            completeAuthentication(message)
        }
      case _ =>
        if (this.availability == DomainAvailability.Offline) {
          Left(DomainNotFound(this.domainId))
        } else {
          Left(DomainUnavailable(this.domainId))
        }
    }
  }

  private[this] def completeAuthentication(message: ConnectionRequest): Either[ConnectionError, ConnectionSuccess] = {
    debug(s"$identityString: Processing authentication request: ${message.credentials.getClass.getSimpleName}")

    val ConnectionRequest(
    _, clientActor, remoteAddress, client, clientVersion, clientMetaData, credentials, _) = message

    val connected = Instant.now()

    val method = message.credentials match {
      case _: JwtAuthRequest => "jwt"
      case _: ReconnectTokenAuthRequest => "reconnect"
      case _: PasswordAuthRequest => "password"
      case _: AnonymousAuthRequest => "anonymous"
    }

    val response = authenticator
      .authenticate(credentials, this.availability)
      .flatMap {
        case authSuccess@ConnectionSuccess(DomainSessionAndUserId(sessionId, userId), _) =>
          debug(s"$identityString: Authenticated user successfully, creating session")
          val session = DomainSession(
            sessionId, userId, connected, None, method, client, clientVersion, clientMetaData, remoteAddress)

          persistenceProvider
            .sessionStore
            .createSession(session)
            .map { _ =>
              debug(s"$identityString: Session created, replying to ClientActor")
              this.disableReceiveTimeout()
              context.watch(clientActor)
              connectedClients.put(clientActor, sessionId)
              Right(authSuccess)
            }
            .recoverWith { cause =>
              error(s"$identityString Unable to authenticate user because a session could not be created.", cause)
              Failure(cause)
            }
            .getOrElse(Left(AuthenticationError("Unable to create user session")))
      }


    debug(s"$identityString: Done processing authentication request: ${message.credentials.getClass.getSimpleName}")
    response
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
    this.activeSessions.addOne(message.sessionId -> Instant.now())
    if (!this.connectedClients.contains(message.clientActor)) {
      this.connectedClients.put(message.clientActor, message.sessionId)
      context.watch(message.clientActor)
    }

    Behaviors.same
  }

  private[this] def removeClient(client: ActorRef[ClientActor.Disconnect]): Behavior[Message] = {
    connectedClients.remove(client) match {
      case Some(sessionId) =>
        debug(s"$identityString: Disconnecting authenticated client : $sessionId")
        persistenceProvider.sessionStore.setSessionDisconnected(sessionId, Instant.now())
        activeSessions.remove(sessionId)
      case None =>
        debug(s"$identityString: Disconnecting unauthenticated client.")
    }

    if (connectedClients.isEmpty && activeSessions.isEmpty) {
      debug(s"$identityString: Last client disconnected from domain, setting receive timeout for passivation.")
      this.enableReceiveTimeout()
    }

    Behaviors.same
  }

  private[this] def onCheckSessions(): Behavior[Message] = {
    val now = Instant.now()
    val expiredSessions = activeSessions.filter { case (_, lastSeen) =>
      val d = Duration.between(lastSeen, now)
      d.compareTo(IdleSessionTimeout) > 1
    }.keySet.toSet

    this.persistenceProvider.sessionStore.setSessionsDisconnected(expiredSessions, now).recover {
      case t: Throwable =>
        error("Error marking dead sessions as disconnected", t)
    }.foreach { _ =>
      expiredSessions.foreach(activeSessions.remove)
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

  private[this] def domainStatusChanged(domainId: DomainId, status: DomainStatus.Value): Behavior[Message] = {
    if (this.domainId == domainId) {
      this.status = status
      this.status match {
        case DomainStatus.Error =>
          debug(s"$identityString: Domain in error state, immediately passivating.")
          passivate()
        case DomainStatus.Deleting =>
          debug(s"$identityString: Domain deleting, immediately passivating.")
          passivate()
        case DomainStatus.SchemaUpgradeRequired =>
          debug(s"$identityString: Domain needs upgrading, immediately passivating.")
          passivate()
        case DomainStatus.SchemaUpgrading =>
          debug(s"$identityString: Domain upgrading, immediately passivating.")
          passivate()
        case DomainStatus.Ready =>
          Behaviors.same
      }
    } else {
      Behaviors.same
    }
  }

  private[this] def domainAvailabilityChanged(domainId: DomainId, availability: DomainAvailability.Value): Behavior[Message] = {
    if (this.domainId == domainId) {
      this.availability = availability
      this.availability match {
        case DomainAvailability.Offline =>
          debug(s"$identityString: Domain going offline, immediately passivating.")
          passivate()
        case DomainAvailability.Maintenance =>
          debug(s"$identityString: Domain in maintenance mode, staying active")
          Behaviors.same
        case DomainAvailability.Online =>
          debug(s"$identityString: Domain in online, staying active")
          Behaviors.same
      }
    } else {
      Behaviors.same
    }
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
            this.activeSessions += (id -> now)
          })
          val msg = CheckSessions(this.domainId)
          val interval = context.system.settings.config.getDuration(
            "convergence.realtime.domain.session-prune-interval")
          timers.startTimerAtFixedRate(msg, scala.concurrent.duration.Duration.fromNanos(interval.toNanos))
        }
      })
  }

  override protected def handleDomainNotFound(msg: Message): Unit = {
    msg match {
      case msg: ConnectionRequest =>
        msg.replyTo ! ConnectionResponse(Left(DomainNotFound(msg.domainId)))
      case _ =>
        warn(s"$identityString: The domain was not found, but also the first message to the domain was not an authentication, so don't know how to respond.")
    }
  }

  override protected def getDomainId(msg: Message): DomainId = msg.domainId

  override protected def getReceiveTimeoutMessage(): Message = ReceiveTimeout(this.domainId)
}

object DomainSessionActor {

  def apply(domainId: DomainId,
            shardRegion: ActorRef[Message],
            shard: ActorRef[ClusterSharding.ShardCommand],
            domainPersistenceManager: DomainPersistenceManager,
            receiveTimeout: FiniteDuration,
            domainLifecycleTopic: ActorRef[DomainLifecycleTopic.TopicMessage]): Behavior[Message] =
    Behaviors.setup { context =>
      Behaviors.withTimers { timers =>
        new DomainSessionActor(
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

  private final case class ReceiveTimeout(domainId: DomainId) extends Message

  private final case class CheckSessions(domainId: DomainId) extends Message

  final case class ClientHeartbeat(domainId: DomainId, sessionId: String, clientActor: ActorRef[ClientActor.Disconnect]) extends Message

  final case class ConnectionRequest(domainId: DomainId,
                                     clientActor: ActorRef[ClientActor.Disconnect],
                                     remoteAddress: String,
                                     client: String,
                                     clientVersion: String,
                                     clientMetaData: String,
                                     credentials: AuthenticationCredentials,
                                     replyTo: ActorRef[ConnectionResponse]) extends Message

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[DomainNotFound], name = "not_found"),
    new JsonSubTypes.Type(value = classOf[DomainDatabaseError], name = "database_error"),
    new JsonSubTypes.Type(value = classOf[DomainUnavailable], name = "unavailable"),
    new JsonSubTypes.Type(value = classOf[AuthenticationFailed], name = "auth_failed"),
    new JsonSubTypes.Type(value = classOf[AuthenticationError], name = "auth_error"),
    new JsonSubTypes.Type(value = classOf[AnonymousAuthenticationDisabled], name = "anonymous_auth_disabled")
  ))
  sealed trait ConnectionError

  final case class DomainNotFound(domainId: DomainId) extends ConnectionError

  final case class DomainDatabaseError(domainId: DomainId) extends ConnectionError

  final case class DomainUnavailable(domainId: DomainId) extends ConnectionError

  final case class AuthenticationFailed() extends ConnectionError

  final case class AuthenticationError(message: String) extends ConnectionError

  final case class AnonymousAuthenticationDisabled() extends ConnectionError

  final case class ConnectionSuccess(session: DomainSessionAndUserId, reconnectToken: Option[String])

  final case class ConnectionResponse(response: Either[ConnectionError, ConnectionSuccess]) extends CborSerializable


  final case class ClientDisconnected(domainId: DomainId, clientActor: ActorRef[ClientActor.Disconnect]) extends Message

  final case class InternalDomainStatusChanged(domainId: DomainId,
                                               status: DomainStatus.Value) extends Message

  final case class InternalDomainAvailabilityChanged(domainId: DomainId, availability: DomainAvailability.Value) extends Message
}
