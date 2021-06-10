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
import com.convergencelabs.convergence.proto.core.AuthenticationRequestMessage._
import com.convergencelabs.convergence.proto.core.AuthenticationResponseMessage.{AuthFailureData, AuthSuccessData}
import com.convergencelabs.convergence.proto.core.HandshakeResponseMessage.ErrorData
import com.convergencelabs.convergence.proto.core._
import com.convergencelabs.convergence.proto.{NormalMessage, ServerMessage, _}
import com.convergencelabs.convergence.server.api.realtime.ProtocolConnection._
import com.convergencelabs.convergence.server.api.realtime.protocol.ConvergenceMessageBodyUtils
import com.convergencelabs.convergence.server.api.realtime.protocol.IdentityProtoConverters._
import com.convergencelabs.convergence.server.api.realtime.protocol.JsonProtoConverters._
import com.convergencelabs.convergence.server.backend.services.domain.DomainActor.AuthenticationFailed
import com.convergencelabs.convergence.server.backend.services.domain._
import com.convergencelabs.convergence.server.backend.services.domain.activity.ActivityActor
import com.convergencelabs.convergence.server.backend.services.domain.chat.{ChatActor, ChatDeliveryActor, ChatManagerActor}
import com.convergencelabs.convergence.server.backend.services.domain.model.{ModelOperationStoreActor, ModelStoreActor, RealtimeModelActor}
import com.convergencelabs.convergence.server.backend.services.domain.presence.{PresenceServiceActor, UserPresence}
import com.convergencelabs.convergence.server.backend.services.server.DomainLifecycleTopic
import com.convergencelabs.convergence.server.model.DomainId
import com.convergencelabs.convergence.server.model.domain.session.DomainSessionAndUserId
import com.convergencelabs.convergence.server.model.domain.user.DomainUser
import com.convergencelabs.convergence.server.model.server.domain.{DomainAvailability, DomainStatus}
import com.convergencelabs.convergence.server.util.UnexpectedErrorException
import com.convergencelabs.convergence.server.util.concurrent.AskHandler
import com.convergencelabs.convergence.server.util.serialization.akka.CborSerializable
import com.google.protobuf.struct.Value
import com.google.protobuf.struct.Value.Kind.StringValue
import grizzled.slf4j.Logging
import scalapb.GeneratedMessage

import java.util.concurrent.{TimeUnit, TimeoutException}
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
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
                                domainRegion: ActorRef[DomainActor.Message],
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

  timers.startSingleTimer(HandshakeTimerKey, HandshakeTimeout, protocolConfig.handshakeTimeout)

  private[this] var modelClient: ActorRef[ModelClientActor.IncomingMessage] = _
  private[this] var identityClient: ActorRef[IdentityClientActor.IncomingMessage] = _
  private[this] var activityClient: ActorRef[ActivityClientActor.IncomingMessage] = _
  private[this] var presenceClient: ActorRef[PresenceClientActor.IncomingMessage] = _
  private[this] var chatClient: ActorRef[ChatClientActor.IncomingMessage] = _
  private[this] var historyClient: ActorRef[HistoricModelClientActor.IncomingMessage] = _

  private[this] var modelStoreActor: ActorRef[ModelStoreActor.Message] = _
  private[this] var operationStoreActor: ActorRef[ModelOperationStoreActor.Message] = _
  private[this] var identityServiceActor: ActorRef[IdentityServiceActor.Message] = _
  private[this] var presenceServiceActor: ActorRef[PresenceServiceActor.Message] = _
  private[this] var identityCacheManager: ActorRef[IdentityCacheManagerActor.Message] = _
  private[this] var chatManagerActor: ActorRef[ChatManagerActor.Message] = _
  private[this] var sessionId: String = _
  private[this] var reconnectToken: Option[String] = None

  private[this] var protocolConnection: ProtocolConnection = _

  private[this] var client: String = _
  private[this] var clientVersion: String = _

  private[this] val shutdownTask = {
    val self = context.self
    CoordinatedShutdown(context.system).addCancellableTask(CoordinatedShutdown.PhaseServiceRequestsDone, "Shutdown Client Actors") { () =>
      debug("ClientActor executing coordinated shutdown: " + this.sessionId)
      if (timers.isTimerActive(HandshakeTimerKey)) {
        timers.cancel(HandshakeTimerKey)
      }
      Option(protocolConnection).foreach(_.dispose())
      domainRegion ! DomainActor.ClientDisconnected(domainId = this.domainId, self)
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
    receiveWhileConnecting.applyOrElse(msg, (_: ClientActor.Message) => Behaviors.unhandled)
  }

  private[this] def receiveWhileConnecting: PartialFunction[ClientActor.Message, Behavior[ClientActor.Message]] = {
    case WebSocketOpened(connectionActor) =>
      this.webSocketActor = connectionActor
      this.protocolConnection = new ProtocolConnection(
        context.self.narrow[FromProtocolConnection],
        connectionActor,
        protocolConfig,
        context.system.scheduler,
        context.executionContext)
      this.messageHandler = handleHandshakeMessage
      Behaviors.receiveMessage(receiveWhileHandshaking)
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

  private[this] val receiveWhileHandshaking: PartialFunction[Message, Behavior[Message]] = {
    case HandshakeTimeout =>
      debug(s"$domainId: Client handshake timeout")
      Option(webSocketActor) match {
        case Some(connection) => connection ! WebSocketService.CloseSocket
        case None =>
      }
      Behaviors.stopped
    case handshakeSuccess: InternalHandshakeSuccess =>
      handleHandshakeSuccess(handshakeSuccess)
    case msg: Message =>
      receiveCommon(msg)
  }

  private[this] val receiveAuthenticationSuccess: PartialFunction[Message, Behavior[Message]] = {
    case authSuccess: InternalAuthSuccess =>
      handleAuthenticationSuccess(authSuccess)
  }

  private[this] val receiveWhileAuthenticating =
    receiveAuthenticationSuccess orElse
      receiveCommon

  private[this] val receiveWhileAuthenticated =
    receiveCommon

  private[this] var messageHandler: MessageHandler = handleHandshakeMessage

  private[this] def handleHandshakeMessage: MessageHandler = {
    case RequestReceived(message, replyCallback) if message.isInstanceOf[HandshakeRequestMessage] =>
      handshake(message.asInstanceOf[HandshakeRequestMessage], replyCallback)
    case _ =>
      Behaviors.unhandled
  }

  private[this] def handleAuthenticationMessage: MessageHandler = {
    case RequestReceived(message, replyCallback) if message.isInstanceOf[AuthenticationRequestMessage] =>
      authenticate(message.asInstanceOf[AuthenticationRequestMessage], replyCallback)
    case msg: Any =>
      invalidMessage("an unexpected messages was received while authenticating", Some(msg))
  }

  private[this] def handleMessagesWhenAuthenticated: MessageHandler = {
    case RequestReceived(message, _) if message.isInstanceOf[HandshakeRequestMessage] =>
      invalidMessage("A handshake message was received while already authenticated", Some(message))
    case RequestReceived(message, _) if message.isInstanceOf[GeneratedMessage with RequestMessage with AuthenticationMessage] =>
      invalidMessage("An authentication message was received while already authenticated", Some(message))

    case message: MessageReceived =>
      onMessageReceived(message)
    case message: RequestReceived =>
      onRequestReceived(message)
  }

  //
  // Handshaking
  //

  private[this] def handshake(request: HandshakeRequestMessage, cb: ReplyCallback): Behavior[Message] = {
    implicit val t: Timeout = domainHandshakeTimeout
    if (timers.isTimerActive(HandshakeTimerKey)) {
      timers.cancel(HandshakeTimerKey)
      debug(s"$domainId: Handshaking with DomainActor")
      domainRegion.ask[DomainActor.HandshakeResponse](DomainActor.HandshakeRequest(domainId, context.self, request.reconnect, request.reconnectToken, _))
        .map(_.handshake.fold(
          { failure =>
            val (code, details) = failure match {
              case DomainActor.DomainNotFound(_) =>
                debug(s"$domainId: Handshake failure: The domain does not exist.")
                ("domain_not_found", s"The domain '${domainId.namespace}/${domainId.domainId}' does not exist")
              case DomainActor.DomainDatabaseError(_) =>
                debug(s"$domainId: Handshake failure: The domain database could not be connected to.")
                ("domain_database_error", s"The domain '${domainId.namespace}/${domainId.domainId}' could not connect to its database.")
              case DomainActor.DomainUnavailable(_) =>
                debug(s"$domainId: Handshake failure: The domain is unavailable.")
                ("domain_unavailable", s"The domain '${domainId.namespace}/${domainId.domainId}' is unavailable, please try again later.")
            }
            cb.reply(HandshakeResponseMessage(success = false, Some(ErrorData(code, details)), retryOk = false))
          },
          { handshake =>
            debug(s"$domainId: Handshake success")
            context.self ! InternalHandshakeSuccess(request.client, request.clientVersion, handshake, cb)
          })
        )
        .recover { cause =>
          error(s"$domainId: Error handshaking with DomainActor", cause)
          cb.reply(HandshakeResponseMessage(success = false, Some(ErrorData("unknown", "An unknown error occurred handshaking with the domain.")), retryOk = true))
        }
      Behaviors.receiveMessage(this.receiveWhileHandshaking)
        .receiveSignal { x => onSignal.apply(x._2) }
    } else {
      debug(s"$domainId: Not handshaking with domain because handshake timeout occurred")
      Behaviors.same
    }
  }

  private[this] def disconnect(): Behavior[Message] = {
    context.self ! Disconnect()
    Behaviors.same
  }

  private[this] def handleDisconnect(): Behavior[Message] = {
    this.webSocketActor ! WebSocketService.CloseSocket
    Behaviors.same
  }

  private[this] def handleHandshakeSuccess(success: InternalHandshakeSuccess): Behavior[Message] = {
    val InternalHandshakeSuccess(
    client,
    clientVersion,
    DomainActor.HandshakeSuccess(modelStoreActor, operationStoreActor, identityActor, presenceActor, chatLookupActor),
    cb) = success

    this.client = client
    this.clientVersion = clientVersion
    this.modelStoreActor = modelStoreActor
    this.operationStoreActor = operationStoreActor
    this.identityServiceActor = identityActor
    this.presenceServiceActor = presenceActor
    this.chatManagerActor = chatLookupActor
    debug(s"$domainId: Sending handshake response to client")

    this.identityCacheManager = context.spawn(IdentityCacheManagerActor(
      context.self.narrow[FromIdentityResolver],
      identityActor,
      identityResolutionTimeout),
      "IdentityCacheManager"
    )

    cb.reply(HandshakeResponseMessage(success = true, None, retryOk = true, this.domainId.namespace, this.domainId.domainId, None))

    this.messageHandler = handleAuthenticationMessage
    Behaviors.receiveMessage(receiveWhileAuthenticating)
      .receiveSignal { x => onSignal.apply(x._2) }
  }

  //
  // Authentication
  //

  private[this] def authenticate(requestMessage: AuthenticationRequestMessage, cb: ReplyCallback): Behavior[Message] = {
    (requestMessage.auth match {
      case AuthenticationRequestMessage.Auth.Password(PasswordAuthRequestData(username, password, _)) =>
        Some(PasswordAuthRequest(username, password))
      case AuthenticationRequestMessage.Auth.Jwt(JwtAuthRequestData(jwt, _)) =>
        Some(JwtAuthRequest(jwt))
      case AuthenticationRequestMessage.Auth.Reconnect(ReconnectTokenAuthRequestData(token, _)) =>
        Some(ReconnectTokenAuthRequest(token))
      case AuthenticationRequestMessage.Auth.Anonymous(AnonymousAuthRequestData(displayName, _)) =>
        Some(AnonymousAuthRequest(displayName))
      case AuthenticationRequestMessage.Auth.Empty =>
        None
    }) match {
      case Some(authCredentials) =>
        implicit val t: Timeout = domainAuthTimeout
        domainRegion
          .ask[DomainActor.AuthenticationResponse](DomainActor.AuthenticationRequest(
            domainId, context.self.narrow[Disconnect], remoteHost.toString, this.client, this.clientVersion, userAgent, authCredentials, _))
          .map(_.response.fold(
            { case AuthenticationFailed(msg) =>
              cb.reply(AuthenticationResponseMessage().withFailure(AuthFailureData(msg.getOrElse(""))))
              // TODO we should probably disconnect here. There is a bit of complexity
              //  where we want to disconnect AFTER this message goes out. The issue
              //  is that there are a few steps to sending a message and we don't
              //  really have a way to know here when tha is done.
              //  see: https://github.com/convergencelabs/convergence-project/issues/140
            },
            { case DomainActor.AuthenticationSuccess(session, reconnectToken) =>
              obtainPresenceAfterAuth(session, reconnectToken, cb)
            }
          ))
          .recover(_ => cb.timeoutError())
      case None =>
        error(s"Invalid authentication message: $requestMessage")
        cb.reply(AuthenticationResponseMessage().withFailure(AuthFailureData("")))
    }

    Behaviors.same
  }

  private[this] def obtainPresenceAfterAuth(session: DomainSessionAndUserId, reconnectToken: Option[String], cb: ReplyCallback): Unit = {
    // Note these are created outside of the for comprehension so that they
    // can execute in parallel.

    implicit val t: Timeout = presenceAfterAuthTimeout

    val presenceFuture = presenceServiceActor
      .ask[PresenceServiceActor.GetPresenceResponse](PresenceServiceActor.GetPresenceRequest(session.userId, _))
      .map(_.presence)
      .handleError(_ => UnexpectedErrorException())

    val userFuture = identityServiceActor
      .ask[IdentityServiceActor.GetUserResponse](IdentityServiceActor.GetUserRequest(session.userId, _))
      .map(_.user)
      .handleError(_ => UnexpectedErrorException())

    (for {
      presence <- presenceFuture
      user <- userFuture
    } yield {
      context.self ! InternalAuthSuccess(user, session, reconnectToken, presence, cb)
    }).recover {
      case cause =>
        error("Error getting user data after successful authentication", cause)
        cb.reply(AuthenticationResponseMessage().withFailure(AuthFailureData("")))
    }
  }

  private[this] def handleAuthenticationSuccess(message: InternalAuthSuccess): Behavior[Message] = {
    val InternalAuthSuccess(user, session, reconnectToken, presence, cb) = message
    val narrowedSelf = context.self.narrow[SendToClient]

    val requestTimeout: Timeout = Timeout(protocolConfig.defaultRequestTimeout)

    this.sessionId = session.sessionId
    this.reconnectToken = reconnectToken
    this.modelClient = context.spawn(ModelClientActor(domainId, session, narrowedSelf, modelStoreActor, modelShardRegion, requestTimeout, modelSyncInterval), "ModelClient")
    this.identityClient = context.spawn(IdentityClientActor(identityServiceActor, requestTimeout), "IdentityClient")
    this.chatClient = context.spawn(ChatClientActor(domainId, session, narrowedSelf, chatShardRegion, chatDeliveryShardRegion, chatManagerActor, requestTimeout), "ChatClient")
    this.activityClient = context.spawn(ActivityClientActor(domainId, session, narrowedSelf, activityShardRegion, requestTimeout), "ActivityClient")
    this.presenceClient = context.spawn(PresenceClientActor(session, narrowedSelf, presenceServiceActor, requestTimeout), "PresenceClient")
    this.historyClient = context.spawn(HistoricModelClientActor(domainId, operationStoreActor, modelShardRegion, requestTimeout), "ModelHistoryClient")
    this.messageHandler = handleMessagesWhenAuthenticated

    val response = AuthenticationResponseMessage().withSuccess(AuthSuccessData(
      Some(domainUserToProto(user)),
      session.sessionId,
      this.reconnectToken.getOrElse(""),
      jValueMapToValueMap(presence.state)))
    cb.reply(response)

    Behaviors.receiveMessage(receiveWhileAuthenticated)
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
            ErrorMessage(errorCode ,errorMessage, errorDetails)
          case e: Throwable =>
            error("Error processing response message: " + message.toString, e)

            val errorCode = ErrorCodes.InvalidMessage.toString
            val errorMessage = "A response message from the client could not be processed."
            ErrorMessage(errorCode ,errorMessage, errorDetails)
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
        val idType = msg.idType
        if (idType == PermissionType.CHAT) {
          chatClient ! ChatClientActor.IncomingProtocolPermissionsRequest(msg, cb)
        }
      case RequestReceived(msg, cb) =>
        cb.expectedError(ErrorCodes.InvalidMessage, "An invalid request was received: " + msg.toString)
    }

    Behaviors.same
  }

  //
  // Error handling
  //

  private def onWebSocketClosed(): Behavior[Message] = {
    debug(s"$domainId: Received a WebSocketClosed message; sending disconnect to domain and stopping: $sessionId")
    // TODO we may want to keep this client alive to smooth over reconnect in the future.

    domainRegion ! DomainActor.ClientDisconnected(domainId, context.self)
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
        case _ =>
          debug(s"$domainId: Disconnecting client because domain availability changed to: " + availability )
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
          debug(s"$domainId: Disconnecting client because domain status changed to: " + status )
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

    if (client == null) {
      // Client is non-null after a successful handshake, so if it
      // is null we can check if the outgoing message is a handshake
      // failure
      convergenceMessage.body.handshakeResponse.foreach { resp =>
        if (!resp.success) {
          webSocketActor ! WebSocketService.CloseSocket
        }
      }
    } else if (this.sessionId == null) {
      // if client was non null, but sessionId is null then we
      // have not successfully authenticated yet. Check for an
      // auth failure.
      convergenceMessage.body.authenticationResponse.foreach { resp =>
        if (resp.response.isFailure) {
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
                              domainRegion: ActorRef[DomainActor.Message],
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


  private final case object HandshakeTimerKey

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
  private final case object HandshakeTimeout extends Message

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

  private final case class InternalAuthSuccess(user: DomainUser,
                                               session: DomainSessionAndUserId,
                                               reconnectToken: Option[String],
                                               presence: UserPresence,
                                               cb: ReplyCallback) extends Message

  private final case class InternalHandshakeSuccess(client: String,
                                                    clientVersion: String,
                                                    handshakeSuccess: DomainActor.HandshakeSuccess,
                                                    cb: ReplyCallback) extends Message

  final case class Disconnect() extends Message

  private final case class InternalDomainStatusChanged(domainId: DomainId, status: DomainStatus.Value) extends Message

  private final case class InternalDomainAvailabilityChanged(domainId: DomainId, availability: DomainAvailability.Value) extends Message
}


