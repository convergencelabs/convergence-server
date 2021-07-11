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

package com.convergencelabs.convergence.server.api.realtime

import akka.Done
import akka.actor.CoordinatedShutdown
import akka.actor.typed._
import akka.actor.typed.pubsub.Topic
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors, TimerScheduler}
import akka.http.scaladsl.model.RemoteAddress
import akka.util.Timeout
import com.convergencelabs.convergence.proto.core.ConnectionRequestMessage._
import com.convergencelabs.convergence.proto.core.ConnectionResponseMessage.{ConnectionFailureData, ConnectionSuccessData, ServerData}
import com.convergencelabs.convergence.proto.core._
import com.convergencelabs.convergence.proto.{NormalMessage, ServerMessage, _}
import com.convergencelabs.convergence.server.BuildInfo
import com.convergencelabs.convergence.server.api.realtime.ProtocolConnection._
import com.convergencelabs.convergence.server.api.realtime.protocol.{CommonProtoConverters, ConvergenceMessageBodyUtils}
import com.convergencelabs.convergence.server.api.realtime.protocol.IdentityProtoConverters._
import com.convergencelabs.convergence.server.api.realtime.protocol.JsonProtoConverters._
import com.convergencelabs.convergence.server.backend.services.domain.DomainSessionActor.{AnonymousAuthenticationDisabled, AuthenticationError, AuthenticationFailed}
import com.convergencelabs.convergence.server.backend.services.domain._
import com.convergencelabs.convergence.server.backend.services.domain.activity.ActivityActor
import com.convergencelabs.convergence.server.backend.services.domain.chat.{ChatActor, ChatDeliveryActor, ChatServiceActor}
import com.convergencelabs.convergence.server.backend.services.domain.identity.IdentityServiceActor
import com.convergencelabs.convergence.server.backend.services.domain.model.{ModelOperationServiceActor, ModelServiceActor, RealtimeModelActor}
import com.convergencelabs.convergence.server.backend.services.domain.presence.{PresenceServiceActor, UserPresence}
import com.convergencelabs.convergence.server.backend.services.server.DomainLifecycleTopic
import com.convergencelabs.convergence.server.model.DomainId
import com.convergencelabs.convergence.server.model.domain.session.DomainSessionAndUserId
import com.convergencelabs.convergence.server.model.domain.user.{DomainUser, DomainUserId}
import com.convergencelabs.convergence.server.model.server.domain.{DomainAvailability, DomainStatus}
import com.convergencelabs.convergence.server.util.UnexpectedErrorException
import com.convergencelabs.convergence.server.util.concurrent.AskHandler
import com.convergencelabs.convergence.server.util.serialization.akka.CborSerializable
import com.google.protobuf.struct.Value
import com.google.protobuf.struct.Value.Kind.StringValue
import grizzled.slf4j.Logging
import scalapb.GeneratedMessage

import java.time.Instant
import java.util.concurrent.{TimeUnit, TimeoutException}
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps
import scala.util.{Failure, Success}

/**
 * The [[ClientActor]] is the serve side representation of a connected client.
 * It translates incoming and outgoing messages and forwards requests on to
 * the appropriate backend systems.
 *
 * @param context                 The ActorContext for this actor.
 * @param timers                  The actors timer subsystem.
 * @param domainId                The id of the domain this client has
 *                                connected to.
 * @param protocolConfig          The server side protocol configuration.
 * @param remoteHost              The address of the remote host.
 * @param userAgent               The HTTP user agent of the connected client.
 * @param domainRegion            The shard region for domains.
 * @param activityShardRegion     The shard region for activities.
 * @param modelShardRegion        The shard region for realtime models.
 * @param chatShardRegion         The shard region for chat actors.
 * @param chatDeliveryShardRegion The shard region to register for chat message
 *                                for the user this client represents.
 * @param domainLifecycleTopic    The pub-sub topic for domain lifecycles.
 * @param modelSyncInterval       The interval at which this client should
 *                                check for model updates.
 */
private final class ClientActor(context: ActorContext[ClientActor.Message],
                                timers: TimerScheduler[ClientActor.Message],
                                domainId: DomainId,
                                protocolConfig: ProtocolConfiguration,
                                remoteHost: RemoteAddress,
                                userAgent: String,
                                domainRegion: ActorRef[DomainSessionActor.Message],
                                modelService: ActorRef[ModelServiceActor.Message],
                                modelOperationService: ActorRef[ModelOperationServiceActor.Message],
                                chatService: ActorRef[ChatServiceActor.Message],
                                identityService: ActorRef[IdentityServiceActor.Message],
                                presenceService: ActorRef[PresenceServiceActor.Message],
                                activityShardRegion: ActorRef[ActivityActor.Message],
                                modelShardRegion: ActorRef[RealtimeModelActor.Message],
                                chatShardRegion: ActorRef[ChatActor.Message],
                                chatDeliveryShardRegion: ActorRef[ChatDeliveryActor.Message],
                                domainLifecycleTopic: ActorRef[DomainLifecycleTopic.TopicMessage],
                                modelSyncInterval: FiniteDuration)
  extends AbstractBehavior[ClientActor.Message](context) with Logging {

  import ClientActor._

  private[this] implicit val ec: ExecutionContext = context.executionContext
  private[this] implicit val system: ActorSystem[_] = context.system

  private[this] val presenceAfterAuthTimeout =
    getTimeoutFromConfig("convergence.realtime.client.auth-presence-timeout")

  private[this] val domainHandshakeTimeout =
    getTimeoutFromConfig("convergence.realtime.client.domain-handshake-timeout")

  private[this] val domainAuthTimeout =
    getTimeoutFromConfig("convergence.realtime.client.domain-authentication-timeout")

  private[this] val identityResolutionTimeout =
    getTimeoutFromConfig("convergence.realtime.client.identity-resolution-timeout")

  private[this] var webSocketActor: ActorRef[WebSocketService.WebSocketMessage] = _

  domainLifecycleTopic ! Topic.Subscribe(context.messageAdapter[DomainLifecycleTopic.Message] {
    case DomainLifecycleTopic.DomainStatusChanged(domainId, status) =>
      InternalDomainStatusChanged(domainId, status)
    case DomainLifecycleTopic.DomainAvailabilityChanged(domainId, availability) =>
      InternalDomainAvailabilityChanged(domainId, availability)
  })

  timers.startSingleTimer(ConnectionTimerKey, ConnectionTimeout, protocolConfig.handshakeTimeout)

  private[this] var modelClient: ActorRef[ModelClientActor.IncomingMessage] = _
  private[this] var identityClient: ActorRef[IdentityClientActor.IncomingMessage] = _
  private[this] var activityClient: ActorRef[ActivityClientActor.IncomingMessage] = _
  private[this] var presenceClient: ActorRef[PresenceClientActor.IncomingMessage] = _
  private[this] var chatClient: ActorRef[ChatClientActor.IncomingMessage] = _
  private[this] var historyClient: ActorRef[HistoricModelClientActor.IncomingMessage] = _

  private[this] var identityCacheManager: ActorRef[IdentityCacheManagerActor.Message] = _

  private[this] var sessionId: String = _
  private[this] var userId: DomainUserId = _
  private[this] var reconnectToken: Option[String] = None

  private[this] var protocolConnection: ProtocolConnection = _

  private[this] var client: String = _
  private[this] var clientVersion: String = _

  private[this] val shutdownTask = {
    val self = context.self
    CoordinatedShutdown(context.system).addCancellableTask(CoordinatedShutdown.PhaseServiceRequestsDone, "Shutdown Client Actors") { () =>
      debug("ClientActor executing coordinated shutdown: " + this.sessionId)
      if (timers.isTimerActive(ConnectionTimerKey)) {
        timers.cancel(ConnectionTimerKey)
      }
      Option(protocolConnection).foreach(_.dispose())
      domainRegion ! DomainSessionActor.ClientDisconnected(domainId = this.domainId, self)
      webSocketActor ! WebSocketService.CloseSocket
      Future.successful(Done)
    }
  }

  //
  // Receive methods
  //

  override def onSignal: PartialFunction[Signal, Behavior[Message]] = {
    case PostStop =>
      debug("ClientActor stopped: " + this.sessionId)
      shutdownTask.cancel()
      Behaviors.same
  }

  override def onMessage(msg: ClientActor.Message): Behavior[ClientActor.Message] = {
    receiveWhileInitializing.applyOrElse(msg, (_: ClientActor.Message) => Behaviors.unhandled)
  }

  private[this] def receiveWhileInitializing: PartialFunction[ClientActor.Message, Behavior[ClientActor.Message]] = {
    case WebSocketOpened(connectionActor) =>
      this.webSocketActor = connectionActor
      this.protocolConnection = new ProtocolConnection(
        context.self.narrow[FromProtocolConnection],
        connectionActor,
        protocolConfig,
        context.system.scheduler,
        context.executionContext)
      this.messageHandler = handleConnectionMessage
      Behaviors.receiveMessage(receiveWhileConnecting)
        .receiveSignal { x => onSignal.apply(x._2) }
  }

  private[this] def receiveCommon: PartialFunction[Message, Behavior[Message]] = {
    case PongTimeout =>
      debug(s"PongTimeout for session: $sessionId")
      this.handleDisconnect()

    case IncomingBinaryMessage(message) =>
      this.protocolConnection.onIncomingMessage(message) match {
        case Success(Some(event)) =>
          messageHandler(event)
        case Success(None) =>
          Behaviors.same
        case Failure(InvalidConvergenceMessageException(message, convergenceMessage)) =>
          invalidMessage(message, Some(convergenceMessage))
        case Failure(MessageDecodingException()) =>
          invalidMessage("Could not deserialize binary protocol message", None)
        case Failure(cause) =>
          invalidMessage(cause.getMessage, None)
      }

    case SendUnprocessedMessage(convergenceMessage) =>
      Option(identityCacheManager) match {
        case Some(icm) =>
          icm ! IdentityCacheManagerActor.OutgoingMessage(convergenceMessage)
        case _ =>
          this.serializeAndSend(convergenceMessage)
      }
      Behaviors.same

    case SendProcessedMessage(convergenceMessage) =>
      this.serializeAndSend(convergenceMessage)
      Behaviors.same

    case SendServerMessage(message) =>
      onOutgoingMessage(message)

    case SendServerRequest(message, replyTo) =>
      onOutgoingRequest(message, replyTo)

    case WebSocketClosed =>
      onWebSocketClosed()

    case WebSocketError(cause) =>
      onConnectionError(cause)

    case InternalDomainStatusChanged(domainId, status) =>
      domainStatusChanged(domainId, status)

    case InternalDomainAvailabilityChanged(domainId, availability) =>
      domainAvailabilityChanged(domainId, availability)

    case Disconnect() =>
      this.handleDisconnect()

    case msg: Any =>
      invalidMessage("An unhandled messages was received", Some(msg))
  }

  private[this] val receiveConnectionSuccessOrTimeout: PartialFunction[Message, Behavior[Message]] = {
    case ConnectionTimeout =>
      debug(s"$domainId: Client connection timeout")
      Option(webSocketActor) match {
        case Some(connection) =>
          connection ! WebSocketService.CloseSocket
        case None =>
      }
      Behaviors.stopped
    case authSuccess: ConnectionSuccess =>
      handleConnectionSuccess(authSuccess)
  }

  private[this] val receiveWhileConnecting =
    receiveConnectionSuccessOrTimeout orElse
      receiveCommon

  private[this] val receiveWhileConnected: PartialFunction[Message, Behavior[Message]] = {
    case Heartbeat =>
      this.domainRegion ! DomainSessionActor.ClientHeartbeat(this.domainId, this.sessionId, context.self)
      Behaviors.same
    case msg =>
      receiveCommon(msg)
  }

  private[this] var messageHandler: MessageHandler = handleConnectionMessage


  private[this] def handleConnectionMessage: MessageHandler = {
    case RequestReceived(message, replyCallback) if message.isInstanceOf[ConnectionRequestMessage] =>
      connectToDomain(message.asInstanceOf[ConnectionRequestMessage], replyCallback)
    case msg: Any =>
      invalidMessage("an unexpected messages was received while connecting", Some(msg))
  }

  private[this] def handleMessagesWhenConnected: MessageHandler = {
    case RequestReceived(message, _) if message.isInstanceOf[ConnectionRequestMessage] =>
      invalidMessage("A connection request message was received while already authenticated", Some(message))
    case RequestReceived(message, _) if message.isInstanceOf[GeneratedMessage with RequestMessage with AuthenticationMessage] =>
      invalidMessage("An authentication message was received while already authenticated", Some(message))

    case message: MessageReceived =>
      onMessageReceived(message)
    case message: RequestReceived =>
      onRequestReceived(message)
  }

  private[this] def disconnect(): Behavior[Message] = {
    context.self ! Disconnect()
    Behaviors.same
  }

  private[this] def handleDisconnect(): Behavior[Message] = {
    this.webSocketActor ! WebSocketService.CloseSocket
    Behaviors.same
  }

  //
  // Authentication
  //

  private[this] def connectToDomain(requestMessage: ConnectionRequestMessage, cb: ReplyCallback): Behavior[Message] = {
    this.client = requestMessage.client
    this.clientVersion = requestMessage.clientVersion

    implicit val t: Timeout = domainHandshakeTimeout
    if (timers.isTimerActive(ConnectionTimerKey)) {
      timers.cancel(ConnectionTimerKey)
      (requestMessage.auth match {
        case ConnectionRequestMessage.Auth.Password(PasswordAuthRequestData(username, password, _)) =>
          Some(PasswordAuthRequest(username, password))
        case ConnectionRequestMessage.Auth.Jwt(JwtAuthRequestData(jwt, _)) =>
          Some(JwtAuthRequest(jwt))
        case ConnectionRequestMessage.Auth.Reconnect(ReconnectTokenAuthRequestData(token, _)) =>
          Some(ReconnectTokenAuthRequest(token))
        case ConnectionRequestMessage.Auth.Anonymous(AnonymousAuthRequestData(displayName, _)) =>
          Some(AnonymousAuthRequest(displayName))
        case ConnectionRequestMessage.Auth.Empty =>
          None
      }) match {
        case Some(authCredentials) =>
          implicit val t: Timeout = domainAuthTimeout
          domainRegion
            .ask[DomainSessionActor.ConnectionResponse](DomainSessionActor.ConnectionRequest(
              domainId, context.self.narrow[Disconnect], remoteHost.toString, this.client, this.clientVersion, userAgent, authCredentials, _))
            .map(_.response.fold(
              { failure =>
                val failureData = failure match {
                  case DomainSessionActor.DomainNotFound(_) =>
                    debug(s"$domainId: Connection failure: The domain does not exist.")
                    ConnectionFailureData(
                      "domain_not_found",
                      s"The domain '${domainId.namespace}/${domainId.domainId}' does not exist",
                      retryOk = false
                    )

                  case DomainSessionActor.DomainDatabaseError(_) =>
                    debug(s"$domainId: Connection failure: The domain database could not be connected to.")
                    ConnectionFailureData(
                      "domain_error",
                      s"The domain '${domainId.namespace}/${domainId.domainId}' could not connect to its database.",
                      retryOk = true
                    )

                  case DomainSessionActor.DomainUnavailable(_) =>
                    debug(s"$domainId: Connection failure: The domain is unavailable.")
                    ConnectionFailureData(
                      "domain_unavailable",
                      s"The domain '${domainId.namespace}/${domainId.domainId}' is unavailable, please try again later.",
                      retryOk = true
                    )

                  case AuthenticationFailed() =>
                    ConnectionFailureData("authentication_failed", "Authentication failed", retryOk = false)

                  case AnonymousAuthenticationDisabled() =>
                    ConnectionFailureData("anonymous_auth_disabled", "Anonymous authentication is disabled for the requested domain.", retryOk = false)

                  case AuthenticationError(msg) =>
                    debug(s"$domainId: Connection failure: " + msg)
                    ConnectionFailureData("authentication_error", msg, retryOk = false)
                }
                cb.reply(ConnectionResponseMessage().withFailure(failureData))
              },
              { case DomainSessionActor.ConnectionSuccess(session, reconnectToken) =>
                obtainPresenceAfterConnection(session, reconnectToken, cb)
              }
            ))
            .recover(_ => cb.timeoutError())
        case None =>
          error(s"Invalid connection message: $requestMessage")
          cb.reply(ConnectionResponseMessage().withFailure(ConnectionFailureData()
            .withCode("invalid auth method")
          ))
      }
      Behaviors.same
    } else {
      debug(s"$domainId: Not connecting client because connection timeout occurred")
      Behaviors.same
    }
  }

  private[this] def obtainPresenceAfterConnection(session: DomainSessionAndUserId, reconnectToken: Option[String], cb: ReplyCallback): Unit = {
    // Note these are created outside of the for comprehension so that they
    // can execute in parallel.

    implicit val t: Timeout = presenceAfterAuthTimeout

    val presenceFuture = this.presenceService
      .ask[PresenceServiceActor.GetPresenceResponse](PresenceServiceActor.GetPresenceRequest(this.domainId, session.userId, _))
      .map(_.presence)
      .handleError(_ => UnexpectedErrorException())

    val userFuture = this.identityService
      .ask[IdentityServiceActor.GetUserResponse](IdentityServiceActor.GetUserRequest(this.domainId, session.userId, _))
      .map(_.user)
      .handleError(_ => UnexpectedErrorException())

    (for {
      presence <- presenceFuture
      user <- userFuture
    } yield {
      context.self ! ConnectionSuccess(user, session, reconnectToken, presence, cb)
    }).recover {
      case cause =>
        error("Error getting user data after successful authentication", cause)
        val failureData = ConnectionFailureData()
          .withCode("server_error")
          .withDetails("The server could not obtain the users presence data")
        cb.reply(ConnectionResponseMessage().withFailure(failureData))
    }
  }

  private[this] def handleConnectionSuccess(message: ConnectionSuccess): Behavior[Message] = {
    val ConnectionSuccess(user, session, reconnectToken, presence, cb) = message
    val narrowedSelf = context.self.narrow[SendToClient]

    val requestTimeout: Timeout = Timeout(protocolConfig.defaultRequestTimeout)

    this.identityCacheManager = context.spawn(IdentityCacheManagerActor(
      domainId,
      context.self.narrow[FromIdentityResolver],
      this.identityService,
      identityResolutionTimeout),
      "IdentityCacheManager"
    )

    this.sessionId = session.sessionId
    this.userId = session.userId
    this.reconnectToken = reconnectToken
    this.modelClient = context.spawn(ModelClientActor(domainId, session, narrowedSelf, modelService, modelShardRegion, requestTimeout, modelSyncInterval), "ModelClient")
    this.identityClient = context.spawn(IdentityClientActor(domainId, identityService, requestTimeout), "IdentityClient")
    this.chatClient = context.spawn(ChatClientActor(domainId, session, narrowedSelf, chatShardRegion, chatDeliveryShardRegion, chatService, requestTimeout), "ChatClient")
    this.activityClient = context.spawn(ActivityClientActor(domainId, session, narrowedSelf, activityShardRegion, requestTimeout), "ActivityClient")
    this.presenceClient = context.spawn(PresenceClientActor(domainId, session, narrowedSelf, presenceService, requestTimeout), "PresenceClient")
    this.historyClient = context.spawn(HistoricModelClientActor(domainId, modelOperationService, modelShardRegion, requestTimeout), "ModelHistoryClient")
    this.messageHandler = handleMessagesWhenConnected

    val response = ConnectionResponseMessage()
      .withSuccess(ConnectionSuccessData(
        domainId.namespace,
        domainId.domainId,
        Some(domainUserToProto(user)),
        session.sessionId,
        this.reconnectToken.getOrElse(""),
        jValueMapToValueMap(presence.state)
      ))
      .withServer(ServerData()
        .withVersion(BuildInfo.version)
      )
    cb.reply(response)

    val interval = context.system.settings.config.getDuration(
      "convergence.realtime.client.heartbeat-interval")
    timers.startTimerAtFixedRate(SessionHeartbeatTimerKey, Heartbeat, Duration.fromNanos(interval.toNanos))

    Behaviors.receiveMessage(receiveWhileConnected)
      .receiveSignal { x => onSignal.apply(x._2) }
  }

  //
  // Incoming / Outgoing Messages
  //

  private[this] def onOutgoingMessage(message: GeneratedMessage with NormalMessage with ServerMessage): Behavior[Message] = {
    protocolConnection.send(message)
    Behaviors.same
  }

  private[this] def onOutgoingRequest(message: GeneratedMessage with RequestMessage with ServerMessage, replyTo: ActorRef[Any]): Behavior[Message] = {
    val f = protocolConnection.request(message)
    f.mapTo[ResponseMessage] onComplete {
      case Success(response) =>
        replyTo ! response

      case Failure(t: Throwable) =>
        val errorDetails = Map("requestMessage" -> Value(StringValue(message.toString)))

        val errorToSend = t match {
          case _: TimeoutException =>
            warn("A request to the client timed out, disconnecting the client. The original message was: " + message.toString)

            val errorCode = ErrorCodes.Timeout.toString
            val errorMessage = "The client didn't respond to a server request in time"
            ErrorMessage(errorCode, errorMessage, errorDetails)
          case e: Throwable =>
            error("Error processing response message: " + message.toString, e)

            val errorCode = ErrorCodes.InvalidMessage.toString
            val errorMessage = "A response message from the client could not be processed."
            ErrorMessage(errorCode, errorMessage, errorDetails)
        }

        this.serializeAndSend(errorToSend)
    }

    Behaviors.same
  }

  private[this] def onMessageReceived(message: MessageReceived): Behavior[Message] = {
    message match {
      case MessageReceived(msg: ModelClientActor.IncomingNormalMessage) =>
        modelClient ! ModelClientActor.IncomingProtocolMessage(msg)
      case MessageReceived(msg: ActivityClientActor.IncomingNormalMessage) =>
        activityClient ! ActivityClientActor.IncomingProtocolMessage(msg)
      case MessageReceived(msg: PresenceClientActor.IncomingNormalMessage) =>
        presenceClient ! PresenceClientActor.IncomingProtocolMessage(msg)
      case MessageReceived(msg) =>
        val errorMessage = ErrorMessage("invalid_message", "An invalid message was received: " + msg.toString)
        protocolConnection.send(errorMessage)
    }
    Behaviors.same
  }

  private[this] def onRequestReceived(message: RequestReceived): Behavior[Message] = {
    message match {
      case RequestReceived(msg: ModelClientActor.IncomingRequestMessage, cb) =>
        modelClient ! ModelClientActor.IncomingProtocolRequest(msg, cb)
      case RequestReceived(msg: IdentityClientActor.IncomingRequest, cb) =>
        identityClient ! IdentityClientActor.IncomingProtocolRequest(msg, cb)
      case RequestReceived(msg: ActivityClientActor.IncomingRequestMessage, cb) =>
        activityClient ! ActivityClientActor.IncomingProtocolRequest(msg, cb)
      case RequestReceived(msg: PresenceClientActor.IncomingRequestMessage, sb) =>
        presenceClient ! PresenceClientActor.IncomingProtocolRequest(msg, sb)
      case RequestReceived(msg: ChatClientActor.IncomingRequestMessage, cb) =>
        chatClient ! ChatClientActor.IncomingProtocolRequest(msg, cb)
      case RequestReceived(msg: HistoricModelClientActor.IncomingRequest, cb) =>
        historyClient ! HistoricModelClientActor.IncomingProtocolRequest(msg, cb)
      case RequestReceived(msg: PermissionRequest with RequestMessage with ClientMessage, cb) =>
        msg.target match {
          case Some(target) =>
            target.targetType match {
              case _: PermissionTarget.TargetType.Activity =>
                activityClient ! ActivityClientActor.IncomingProtocolPermissionsRequest(msg, cb)
              case _: PermissionTarget.TargetType.Chat =>
                chatClient ! ChatClientActor.IncomingProtocolPermissionsRequest(msg, cb)
              case PermissionTarget.TargetType.Empty =>
                cb.expectedError(
                  ErrorCodes.InvalidMessage,
                  "A permission request was received that did not have the target set."
                )
            }
          case None =>
            cb.expectedError(
              ErrorCodes.InvalidMessage,
              "A permission request was received that did not have the target set."
            )
        }
      case RequestReceived(_: GetServerTimeRequestMessage, cb) =>
        onGetServerTimeRequest(cb)
      case msg =>
        logger.error("Unexpected request message received: " + msg)
    }
    Behaviors.same
  }

  private def onGetServerTimeRequest(cb: ReplyCallback): Behavior[Message] = {
    val timestamp = CommonProtoConverters.instantToTimestamp(Instant.now())
    val response = GetServerTimeResponseMessage(Some(timestamp))
    cb.reply(response)
    Behaviors.same
  }

  //
  // Error handling
  //

  private def onWebSocketClosed(): Behavior[Message] = {
    debug(s"$domainId: Received a WebSocketClosed message; sending disconnect to domain and stopping: $sessionId")
    // TODO we may want to keep this client alive to smooth over reconnect in the future.

    domainRegion ! DomainSessionActor.ClientDisconnected(domainId, context.self)
    this.protocolConnection.dispose()

    Behaviors.stopped
  }

  private[this] def onConnectionError(cause: Throwable): Behavior[Message] = {
    debug(s"$domainId: Connection Error for: $sessionId - ${cause.getMessage}")
    this.disconnect()
  }

  private[this] def invalidMessage(details: String, message: Option[Any]): Behavior[Message] = {
    error(s"$domainId: Invalid message. $details: '${message.getOrElse("")}'")
    this.disconnect()
  }

  private[this] def domainAvailabilityChanged(domainId: DomainId, availability: DomainAvailability.Value): Behavior[Message] = {
    if (this.domainId == domainId) {
      availability match {
        case DomainAvailability.Online =>
          Behaviors.same
        case x if x == DomainAvailability.Maintenance && this.userId.isConvergence =>
          Behaviors.same
        case _ =>
          debug(s"$domainId: Disconnecting client because domain availability changed to: " + availability)
          this.disconnect()
      }
    } else {
      Behaviors.same
    }
  }

  private[this] def domainStatusChanged(domainId: DomainId, status: DomainStatus.Value): Behavior[Message] = {
    if (this.domainId == domainId) {
      status match {
        case DomainStatus.Ready =>
          Behaviors.same
        case _ =>
          debug(s"$domainId: Disconnecting client because domain status changed to: " + status)
          this.disconnect()
      }
    } else {
      Behaviors.same
    }
  }

  private[this] def getTimeoutFromConfig(path: String): Timeout = {
    Timeout(system.settings.config.getDuration(path).toNanos, TimeUnit.NANOSECONDS)
  }

  private[this] def serializeAndSend(message: ServerNormalMessage): Unit = {
    val body = ConvergenceMessageBodyUtils.toBody(message)
    val convergenceMessage = ConvergenceMessage().withBody(body)
    this.serializeAndSend(convergenceMessage)
  }

  private[this] def serializeAndSend(convergenceMessage: ConvergenceMessage): Unit = {
    this.protocolConnection.serializeAndSend(convergenceMessage)

    // We check for these two explicit message so we can close the web
    // socket on other of these. We do this here and not elsewhere
    // because sending messages is often a multi-step process.  This
    // is the last step before the message is sent to the web socket
    // actor.  With akka message ordering, this ensures that our
    // message will get to the web socket actor and sent before
    // the close message.

    if (sessionId == null) {
      // Client is non-null after a successful connection, so if it
      // is null we can check if the outgoing message is a connection
      // failure
      convergenceMessage.body.connectionResponse.foreach { resp =>
        if (resp.response.failure.isDefined) {
          webSocketActor ! WebSocketService.CloseSocket
        }
      }
    }
  }
}

object ClientActor {
  private[realtime] def apply(domain: DomainId,
                              protocolConfig: ProtocolConfiguration,
                              remoteHost: RemoteAddress,
                              userAgent: String,
                              domainRegion: ActorRef[DomainSessionActor.Message],
                              modelService: ActorRef[ModelServiceActor.Message],
                              modelOperationService: ActorRef[ModelOperationServiceActor.Message],
                              chatService: ActorRef[ChatServiceActor.Message],
                              identityService: ActorRef[IdentityServiceActor.Message],
                              presenceService: ActorRef[PresenceServiceActor.Message],
                              activityShardRegion: ActorRef[ActivityActor.Message],
                              modelShardRegion: ActorRef[RealtimeModelActor.Message],
                              chatShardRegion: ActorRef[ChatActor.Message],
                              chatDeliveryShardRegion: ActorRef[ChatDeliveryActor.Message],
                              domainLifecycleTopic: ActorRef[DomainLifecycleTopic.TopicMessage],
                              modelSyncInterval: FiniteDuration): Behavior[ClientActor.Message] = {
    Behaviors.setup(context => Behaviors.withTimers(timers =>
      new ClientActor(
        context,
        timers,
        domain,
        protocolConfig,
        remoteHost,
        userAgent,
        domainRegion,
        modelService: ActorRef[ModelServiceActor.Message],
        modelOperationService: ActorRef[ModelOperationServiceActor.Message],
        chatService: ActorRef[ChatServiceActor.Message],
        identityService: ActorRef[IdentityServiceActor.Message],
        presenceService: ActorRef[PresenceServiceActor.Message],
        activityShardRegion,
        modelShardRegion,
        chatShardRegion,
        chatDeliveryShardRegion,
        domainLifecycleTopic,
        modelSyncInterval)
    ))
  }

  private type MessageHandler = PartialFunction[ProtocolMessageEvent, Behavior[Message]]

  private[realtime] type IncomingMessage = GeneratedMessage with NormalMessage with ClientMessage

  private[realtime] final case class IncomingProtocolMessage(message: IncomingMessage) extends Message

  private[realtime] type IncomingRequest = GeneratedMessage with RequestMessage with ClientMessage

  private[realtime] final case class IncomingProtocolRequest(message: IncomingRequest, replyCallback: ReplyCallback) extends Message


  private final case object ConnectionTimerKey

  private final case object SessionHeartbeatTimerKey

  /////////////////////////////////////////////////////////////////////////////
  // Message Protocol
  /////////////////////////////////////////////////////////////////////////////

  /**
   * The parent trait of all messages sent to the ClientActor.
   */
  sealed trait Message extends CborSerializable

  /**
   * Indicates that a handshake timeout with the connecting client has occurred.
   */
  private final case object ConnectionTimeout extends Message

  /**
   * Indicates that the actor should sent a heartbeat to the domain.
   */
  private final case object Heartbeat extends Message

  /**
   * Defines the messages that will come in from the WebSocketService
   */
  private[realtime] sealed trait WebSocketMessage extends Message

  /**
   * Indicates that the connection should now be open and use he supplied
   * ActorRef to send outgoing messages too.
   *
   * @param webSocket The ActorRef to use to send outgoing messages
   *                  to the Web Socket.
   */
  private[realtime] final case class WebSocketOpened(webSocket: ActorRef[WebSocketService.WebSocketMessage]) extends WebSocketMessage

  /**
   * Indicates that the Web Socket for this connection has been closed.
   */
  private[realtime] case object WebSocketClosed extends WebSocketMessage

  /**
   * Indicates that the Web Socket associated with this connection emitted an
   * error.
   *
   * @param cause The cause of the error.
   */
  private[realtime] final case class WebSocketError(cause: Throwable) extends WebSocketMessage

  /**
   * Represents an incoming binary message from the web socket.
   *
   * @param data The incoming binary web socket message data.
   */
  private[realtime] final case class IncomingBinaryMessage(data: Array[Byte]) extends WebSocketMessage


  private[realtime] sealed trait SendToClient extends Message

  private[realtime] final case class SendServerMessage(message: GeneratedMessage with NormalMessage with ServerMessage) extends SendToClient

  private[realtime] final case class SendServerRequest(message: GeneratedMessage with RequestMessage with ServerMessage, replyTo: ActorRef[Any]) extends SendToClient


  /**
   * Messages sent to this actor from the ProtocolConnection. This trait is used
   * to narrow the ClientActor's ref some that the ProtocolConnection can only
   * send certain messages to the ClientActor.
   */
  private[realtime] sealed trait FromProtocolConnection extends Message

  private[realtime] final case object PongTimeout extends FromProtocolConnection

  private[realtime] final case class SendUnprocessedMessage(message: ConvergenceMessage) extends FromProtocolConnection

  private[realtime] sealed trait FromIdentityResolver extends Message

  private[realtime] final case class SendProcessedMessage(message: ConvergenceMessage) extends FromIdentityResolver

  private[realtime] final case class IdentityResolutionError() extends FromIdentityResolver

  private final case class ConnectionSuccess(user: DomainUser,
                                             session: DomainSessionAndUserId,
                                             reconnectToken: Option[String],
                                             presence: UserPresence,
                                             cb: ReplyCallback) extends Message

  final case class Disconnect() extends Message

  private final case class InternalDomainStatusChanged(domainId: DomainId, status: DomainStatus.Value) extends Message

  private final case class InternalDomainAvailabilityChanged(domainId: DomainId, availability: DomainAvailability.Value) extends Message
}


