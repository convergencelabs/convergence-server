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
import com.convergencelabs.convergence.server.ProtocolConfiguration
import com.convergencelabs.convergence.server.datastore.domain.{ModelOperationStoreActor, ModelStoreActor}
import com.convergencelabs.convergence.server.db.provision.DomainLifecycleTopic
import com.convergencelabs.convergence.server.domain._
import com.convergencelabs.convergence.server.domain.activity.ActivityActor
import com.convergencelabs.convergence.server.domain.chat.{ChatActor, ChatDeliveryActor, ChatManagerActor}
import com.convergencelabs.convergence.server.domain.model.RealtimeModelActor
import com.convergencelabs.convergence.server.domain.presence.{PresenceServiceActor, UserPresence}
import com.convergencelabs.convergence.server.util.concurrent.{AskHandler, UnexpectedErrorException}
import grizzled.slf4j.Logging
import scalapb.GeneratedMessage

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.{Failure, Success}

/**
 * The [[ClientActor]] is the serve side representation of a connected client.
 *
 * @param domainId       The id of the domain this client has connected to.
 * @param protocolConfig The server side protocol configuration.
 * @param remoteHost     The address of the remote host.
 * @param userAgent      The HTTP user agent of the connected client.
 */
class ClientActor private(context: ActorContext[ClientActor.Message],
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

  type MessageHandler = PartialFunction[ProtocolMessageEvent, Behavior[Message]]

  // FIXME hard-coded (used for auth and handshake)
  private[this] implicit val requestTimeout: Timeout = Timeout(protocolConfig.defaultRequestTimeout)
  private[this] implicit val ec: ExecutionContext = context.executionContext
  private[this] implicit val system: ActorSystem[_] = context.system

  private[this] var connectionActor: ActorRef[ConnectionActor.ClientMessage] = _

  domainLifecycleTopic ! Topic.Subscribe(context.messageAdapter[DomainLifecycleTopic.Message] {
    case DomainLifecycleTopic.DomainDeleted(id) =>
      DomainDeleted(id)
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

  //
  // Receive methods
  //

  override def onSignal: PartialFunction[Signal, Behavior[Message]] = {
    case PostStop =>
      debug(s"ClientActor($domainId/${this.sessionId}): Stopped")
      if (timers.isTimerActive(HandshakeTimerKey)) {
        timers.cancel(HandshakeTimerKey)
      }
      Option(protocolConnection).foreach(_.dispose())
      Behaviors.same
  }

  override def onMessage(msg: ClientActor.Message): Behavior[ClientActor.Message] = {
    receiveWhileConnecting.applyOrElse(msg, (_: ClientActor.Message) => Behaviors.unhandled)
  }

  private[this] def receiveWhileConnecting: PartialFunction[ClientActor.Message, Behavior[ClientActor.Message]] = {
    case ConnectionOpened(connectionActor) =>
      this.connectionActor = connectionActor
      this.protocolConnection = new ProtocolConnection(
        context.self.narrow[FromProtocolConnection],
        connectionActor,
        protocolConfig,
        context.system.scheduler,
        context.executionContext)
      this.messageHandler = handleHandshakeMessage
      Behaviors.receiveMessage(receiveWhileHandshaking)
        .receiveSignal{x => onSignal.apply(x._2)}
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
        case Failure(cause) =>
          invalidMessage(cause)
      }

    case SendUnprocessedMessage(convergenceMessage) =>
      Option(identityCacheManager) match {
        case Some(icm) =>
          icm ! IdentityCacheManagerActor.OutgoingMessage(convergenceMessage)
        case _ =>
          this.protocolConnection.serializeAndSend(convergenceMessage)
      }
      Behaviors.same

    case SendProcessedMessage(convergenceMessage) =>
      this.protocolConnection.serializeAndSend(convergenceMessage)
      Behaviors.same

    case SendServerMessage(message) =>
      onOutgoingMessage(message)

    case SendServerRequest(message, replyTo) =>
      onOutgoingRequest(message, replyTo)

    case ConnectionClosed =>
      onConnectionClosed()

    case ConnectionError(cause) =>
      onConnectionError(cause)

    case DomainDeleted(domainId) =>
      domainDeleted(domainId)

    case Disconnect() =>
      this.handleDisconnect()

    case x: Any =>
      invalidMessage(x)
  }

  private[this] val receiveWhileHandshaking: PartialFunction[Message, Behavior[Message]] = {
    case HandshakeTimeout =>
      debug(s"$domainId: Client handshake timeout")
      Option(connectionActor) match {
        case Some(connection) => connection ! ConnectionActor.CloseConnection
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
    case x: Any =>
      invalidMessage(x)
  }

  private[this] def handleMessagesWhenAuthenticated: MessageHandler = {
    case RequestReceived(message, _) if message.isInstanceOf[HandshakeRequestMessage] =>
      invalidMessage(message)
    case RequestReceived(message, _) if message.isInstanceOf[GeneratedMessage with RequestMessage with AuthenticationMessage] =>
      invalidMessage(message)

    case message: MessageReceived =>
      onMessageReceived(message)
    case message: RequestReceived =>
      onRequestReceived(message)
  }

  //
  // Handshaking
  //

  private[this] def handshake(request: HandshakeRequestMessage, cb: ReplyCallback): Behavior[Message] = {
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
                ("domain_not_found", s"The domain '${domainId.namespace}/${domainId.domainId}' could not connect to its database.")
              case DomainActor.DomainUnavailable(_) =>
                debug(s"$domainId: Handshake failure: The domain is unavailable.")
                ("domain_not_found", s"The domain '${domainId.namespace}/${domainId.domainId}' is unavailable, please try again later.")
            }
            cb.reply(HandshakeResponseMessage(success = false, Some(ErrorData(code, details)), retryOk = false))
            this.disconnect()
          },
          { handshake =>
            debug(s"$domainId: Handshake success")
            context.self ! InternalHandshakeSuccess(request.client, request.clientVersion, handshake, cb)
          })
        )
        .recover { cause =>
          error(s"$domainId: Error handshaking with DomainActor", cause)
          cb.reply(HandshakeResponseMessage(success = false, Some(ErrorData("unknown", "An unknown error occurred handshaking with the domain.")), retryOk = true))
          this.disconnect()
        }
      Behaviors.receiveMessage(this.receiveWhileHandshaking)
        .receiveSignal{x => onSignal.apply(x._2)}
    } else {
      debug(s"$domainId: Not handshaking with domain because handshake timeout occurred")
      Behaviors.same
    }
  }

  // TODO add an optional message to send to the client.
  private[this] def disconnect(): Behavior[Message] = {
    // TODO we do this to allow outgoing messages to be flushed
    //   What we SHOULD do is send a message to the protocol connection and then have it shut down
    //   when it processes that message. That would cause it to flush any messages in the queue
    //   before shutting down.
    timers.startSingleTimer(Disconnect(), 10 seconds)
    Behaviors.same
  }

  private[this] def handleDisconnect(): Behavior[Message] = {
    this.connectionActor ! ConnectionActor.CloseConnection
    Behaviors.stopped
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
      requestTimeout),
      "IdentityCacheManager"
    )

    // FIXME Protocol Config??
    cb.reply(HandshakeResponseMessage(success = true, None, retryOk = true, this.domainId.namespace, this.domainId.domainId, None))

    this.messageHandler = handleAuthenticationMessage
    Behaviors.receiveMessage(receiveWhileAuthenticating)
      .receiveSignal{x => onSignal.apply(x._2)}
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
        // FIXME if authentication fails we should probably stop the actor
        //  and or shut down the connection?
        domainRegion
          .ask[DomainActor.AuthenticationResponse](DomainActor.AuthenticationRequest(
          domainId, context.self.narrow[Disconnect], remoteHost.toString, this.client, this.clientVersion, userAgent, authCredentials, _))
            .map(_.response.fold(
              { _ =>
                cb.reply(AuthenticationResponseMessage().withFailure(AuthFailureData("")))
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

  private[this] def obtainPresenceAfterAuth(session: DomainUserSessionId, reconnectToken: Option[String], cb: ReplyCallback): Unit = {
    (for {
      presence <- presenceServiceActor.ask[PresenceServiceActor.GetPresenceResponse](PresenceServiceActor.GetPresenceRequest(session.userId, _))
        .map(_.presence)
        .handleError(_ => UnexpectedErrorException())
      user <- identityServiceActor.ask[IdentityServiceActor.GetUserResponse](IdentityServiceActor.GetUserRequest(session.userId, _))
        .map(_.user)
        .handleError(_ => UnexpectedErrorException())
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

    this.sessionId = session.sessionId
    this.reconnectToken = reconnectToken
    this.modelClient = context.spawn(ModelClientActor(domainId, session, narrowedSelf, modelStoreActor, modelShardRegion, requestTimeout, modelSyncInterval), "ModelClient")
    this.identityClient = context.spawn(IdentityClientActor(identityServiceActor), "IdentityClient")
    this.chatClient = context.spawn(ChatClientActor(domainId, session, narrowedSelf, chatShardRegion, chatDeliveryShardRegion, chatManagerActor, requestTimeout), "ChatClient")
    this.activityClient = context.spawn(ActivityClientActor(domainId, session, narrowedSelf, activityShardRegion, requestTimeout), "ActivityClient")
    this.presenceClient = context.spawn(PresenceClientActor(domainId, session, narrowedSelf, presenceServiceActor, requestTimeout), "PresenceClient")
    this.historyClient = context.spawn(HistoricModelClientActor(domainId, session, narrowedSelf, modelStoreActor, operationStoreActor, modelShardRegion, requestTimeout), "ModelHistoryClient")
    this.messageHandler = handleMessagesWhenAuthenticated

    val response = AuthenticationResponseMessage().withSuccess(AuthSuccessData(
      Some(ImplicitMessageConversions.mapDomainUser(user)),
      session.sessionId,
      this.reconnectToken.getOrElse(""),
      JsonProtoConverter.jValueMapToValueMap(presence.state)))
    cb.reply(response)

    Behaviors.receiveMessage(receiveWhileAuthenticated)
      .receiveSignal{x => onSignal.apply(x._2)}
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
      case Failure(cause) =>
        error("Error processing a response message", cause)
        this.protocolConnection.send(ErrorMessage("invalid_response", "Error processing a response", Map()))
        this.connectionActor ! ConnectionActor.CloseConnection
        context.self ! Disconnect()
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
      case _: Any =>
      // TODO send an error back
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
      case message: Any =>
      // TODO send an error back
    }

    Behaviors.same
  }

  //
  // Error handling
  //

  private[this] def onConnectionClosed(): Behavior[Message] = {
    debug(s"$domainId: Received a ConnectionClosed; sending disconnect to domain and stopping: $sessionId")
    domainRegion ! DomainActor.ClientDisconnected(domainId, context.self)

    // TODO we may want to keep this client alive to smooth over reconnect in the future.
    Behaviors.stopped
  }

  private[this] def onConnectionError(cause: Throwable): Behavior[Message] = {
    debug(s"$domainId: Connection Error for: $sessionId - ${cause.getMessage}")
    domainRegion ! DomainActor.ClientDisconnected(domainId, context.self)
    this.disconnect()
  }

  private[this] def invalidMessage(message: Any): Behavior[Message] = {
    error(s"$domainId: Invalid message: '$message'")
    this.disconnect()
  }

  private[this] def domainDeleted(domainId: DomainId): Behavior[Message] = {
    if (this.domainId == domainId) {
      error(s"$domainId: Domain deleted shutting down")
      this.disconnect()
    } else {
      Behaviors.same
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

  private case object HandshakeTimerKey

  /////////////////////////////////////////////////////////////////////////////
  // Message Protocol
  /////////////////////////////////////////////////////////////////////////////

  sealed trait Message

  private case object HandshakeTimeout extends Message

  type IncomingMessage = GeneratedMessage with NormalMessage with ClientMessage

  case class IncomingProtocolMessage(message: IncomingMessage) extends Message

  type IncomingRequest = GeneratedMessage with RequestMessage with ClientMessage

  case class IncomingProtocolRequest(message: IncomingRequest, replyCallback: ReplyCallback) extends Message

  sealed trait ConnectionMessage extends Message

  case class ConnectionOpened(connectionActor: ActorRef[ConnectionActor.ClientMessage]) extends ConnectionMessage

  case object ConnectionClosed extends ConnectionMessage

  case class ConnectionError(cause: Throwable) extends ConnectionMessage


  sealed trait SendToClient extends Message

  case class SendServerMessage(message: GeneratedMessage with NormalMessage with ServerMessage) extends SendToClient

  case class SendServerRequest(message: GeneratedMessage with RequestMessage with ServerMessage, replyTo: ActorRef[Any]) extends SendToClient

  /**
   * Represents an incoming binary message from the client.
   *
   * @param data The incoming binary web socket message data.
   */
  case class IncomingBinaryMessage(data: Array[Byte]) extends ConnectionMessage


  sealed trait FromProtocolConnection extends Message

  case object PongTimeout extends FromProtocolConnection

  case class SendUnprocessedMessage(message: ConvergenceMessage) extends FromProtocolConnection

  sealed trait FromIdentityResolver extends Message

  case class SendProcessedMessage(message: ConvergenceMessage) extends FromIdentityResolver

  case class IdentityResolutionError() extends FromIdentityResolver

  case class InternalAuthSuccess(user: DomainUser,
                                 session: DomainUserSessionId,
                                 reconnectToken: Option[String],
                                 presence: UserPresence,
                                 cb: ReplyCallback) extends Message

  case class InternalHandshakeSuccess(client: String,
                                      clientVersion: String,
                                      handshakeSuccess: DomainActor.HandshakeSuccess,
                                      cb: ReplyCallback) extends Message

  case class Disconnect() extends Message

  case class DomainDeleted(domainId: DomainId) extends Message

}


