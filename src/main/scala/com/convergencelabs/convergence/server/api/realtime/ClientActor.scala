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

import akka.actor.{Actor, ActorLogging, ActorRef, PoisonPill, Props, actorRef2Scala}
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.{Subscribe, SubscribeAck}
import akka.http.scaladsl.model.RemoteAddress
import akka.pattern.ask
import akka.util.Timeout
import com.convergencelabs.convergence.proto._
import com.convergencelabs.convergence.proto.core.AuthenticationRequestMessage.{AnonymousAuthRequestData, JwtAuthRequestData, PasswordAuthRequestData, ReconnectTokenAuthRequestData}
import com.convergencelabs.convergence.proto.core.AuthenticationResponseMessage.{AuthFailureData, AuthSuccessData}
import com.convergencelabs.convergence.proto.core.HandshakeResponseMessage.ErrorData
import com.convergencelabs.convergence.proto.core._
import com.convergencelabs.convergence.server.ProtocolConfiguration
import com.convergencelabs.convergence.server.actor.CborSerializable
import com.convergencelabs.convergence.server.api.realtime.ConnectionActor._
import com.convergencelabs.convergence.server.datastore.EntityNotFoundException
import com.convergencelabs.convergence.server.db.provision.DomainProvisionerActor.{DomainDeleted, domainTopic}
import com.convergencelabs.convergence.server.domain.IdentityServiceActor.{GetUserRequest, GetUserResponse}
import com.convergencelabs.convergence.server.domain._
import com.convergencelabs.convergence.server.domain.activity.ActivityActorSharding
import com.convergencelabs.convergence.server.domain.presence.{GetPresenceRequest, GetPresenceResponse, UserPresence}
import com.convergencelabs.convergence.server.util.concurrent.AskFuture
import scalapb.GeneratedMessage

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
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
private[realtime] class ClientActor(private[this] val domainId: DomainId,
                                    private[this] val protocolConfig: ProtocolConfiguration,
                                    private[this] val remoteHost: RemoteAddress,
                                    private[this] val userAgent: String,
                                    private[this] val modelSyncInterval: FiniteDuration)
  extends Actor with ActorLogging {

  import ClientActor._

  type MessageHandler = PartialFunction[ProtocolMessageEvent, Unit]

  // FIXME hard-coded (used for auth and handshake)
  private[this] implicit val requestTimeout: Timeout = Timeout(protocolConfig.defaultRequestTimeout)
  private[this] implicit val ec: ExecutionContext = context.dispatcher

  private[this] var connectionActor: ActorRef = _

  private[this] val mediator = DistributedPubSub(context.system).mediator
  mediator ! Subscribe(domainTopic(domainId), self)

  // FIXME this should probably be for handshake and auth.
  private[this] val handshakeTimeoutTask =
    context.system.scheduler.scheduleOnce(protocolConfig.handshakeTimeout) {
      log.debug(s"$domainId: Client handshake timeout")
      Option(connectionActor) match {
        case Some(connection) => connection ! CloseConnection
        case None =>
      }
      context.stop(self)
    }

  private[this] var modelClient: ActorRef = _
  private[this] var userClient: ActorRef = _
  private[this] var activityClient: ActorRef = _
  private[this] var presenceClient: ActorRef = _
  private[this] var chatClient: ActorRef = _
  private[this] var historyClient: ActorRef = _

  private[this] var modelStoreActor: ActorRef = _
  private[this] var operationStoreActor: ActorRef = _
  private[this] var identityServiceActor: ActorRef = _
  private[this] var presenceServiceActor: ActorRef = _
  private[this] var identityCacheManager: ActorRef = _
  private[this] var chatLookupActor: ActorRef = _
  private[this] var sessionId: String = _
  private[this] var reconnectToken: Option[String] = None

  private[this] var protocolConnection: ProtocolConnection = _
  private[this] val domainRegion = DomainActorSharding.shardRegion(context.system)

  private[this] var client: String = _
  private[this] var clientVersion: String = _

  //
  // Receive methods
  //
  def receive: Receive = receiveWhileConnecting orElse receiveCommon

  private[this] def receiveWhileConnecting: Receive = {
    case WebSocketOpened(connectionActor) =>
      this.connectionActor = connectionActor
      this.protocolConnection = new ProtocolConnection(
        self,
        connectionActor,
        protocolConfig,
        context.system.scheduler,
        context.dispatcher)
      context.become(receiveWhileHandshaking)
  }

  private[this] def receiveCommon: Receive = {
    case PongTimeout =>
      log.debug(s"Pong Timeout for session: $sessionId")
      this.handleDisconnect()

    case IncomingBinaryMessage(message) =>
      this.protocolConnection.onIncomingMessage(message) match {
        case Success(Some(event)) =>
          messageHandler(event)
        case Success(None) =>
        // No Op
        case Failure(cause) =>
          invalidMessage(cause)
      }

    case SendUnprocessedMessage(convergenceMessage) =>
      Option(identityCacheManager) match {
        case Some(icm) => icm ! convergenceMessage
        case _ => this.protocolConnection.serializeAndSend(convergenceMessage)
      }

    case SendProcessedMessage(convergenceMessage) =>
      this.protocolConnection.serializeAndSend(convergenceMessage)

    case message: GeneratedMessage with NormalMessage =>
      onOutgoingMessage(message)

    case message: GeneratedMessage with RequestMessage =>
      onOutgoingRequest(message)

    case WebSocketClosed =>
      log.debug(s"$domainId: WebSocketClosed for session: $sessionId")
      onConnectionClosed()

    case WebSocketError(cause) =>
      onConnectionError(cause)

    case SubscribeAck(_) =>
    // no-op

    case DomainDeleted(_) =>
      domainDeleted()

    case Disconnect =>
      this.handleDisconnect()

    case x: Any =>
      invalidMessage(x)
  }

  private[this] val receiveHandshakeSuccess: Receive = {
    case handshakeSuccess: InternalHandshakeSuccess =>
      handleHandshakeSuccess(handshakeSuccess)
  }

  private[this] val receiveWhileHandshaking =
    receiveHandshakeSuccess orElse
      receiveCommon

  private[this] val receiveAuthenticationSuccess: Receive = {
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
    case x: Any => invalidMessage(x)
  }

  private[this] def handleAuthenticationMessage: MessageHandler = {
    case RequestReceived(message, replyCallback) if message.isInstanceOf[AuthenticationRequestMessage] =>
      authenticate(message.asInstanceOf[AuthenticationRequestMessage], replyCallback)
    case x: Any => invalidMessage(x)
  }

  private[this] def handleMessagesWhenAuthenticated: MessageHandler = {
    case RequestReceived(message, _) if message.isInstanceOf[HandshakeRequestMessage] => invalidMessage(message)
    case RequestReceived(message, _) if message.isInstanceOf[GeneratedMessage with RequestMessage with AuthenticationMessage] => invalidMessage(message)

    case message: MessageReceived => onMessageReceived(message)
    case message: RequestReceived => onRequestReceived(message)
  }

  //
  // Handshaking
  //

  private[this] def handshake(request: HandshakeRequestMessage, cb: ReplyCallback): Unit = {
    val canceled = handshakeTimeoutTask.cancel()
    if (canceled) {
      log.debug(s"$domainId: Handshaking with DomainActor")
      val future = domainRegion ? HandshakeRequest(domainId, self, request.reconnect, request.reconnectToken)

      future.mapResponse[HandshakeSuccess] onComplete {
        case Success(success) =>
          log.debug(s"$domainId: Handshake success")
          self ! InternalHandshakeSuccess(request.client, request.clientVersion, success, cb)
        case Failure(HandshakeFailureException(code, details)) =>
          log.debug(s"$domainId: Handshake failure: {code: '$code', details: '$details'}")
          cb.reply(HandshakeResponseMessage(success = false, Some(ErrorData(code, details)), retryOk = false))
          this.disconnect()
        case Failure(cause) =>
          log.error(cause, s"$domainId: Error handshaking with DomainActor")
          cb.reply(HandshakeResponseMessage(success = false, Some(ErrorData("unknown", "unknown error")), retryOk = true))
          this.disconnect()
      }
    } else {
      log.debug(s"$domainId: Not handshaking with domain because handshake timeout occurred")
    }
  }

  // TODO add an optional message to send to the client.
  private[this] def disconnect(): Unit = {
    // TODO we do this to allow outgoing messages to be flushed
    //   What we SHOULD do is send a message to the protocol connection and then have it shut down
    //   when it processes that message. That would cause it to flush any messages in the queue
    //   before shutting down.
    this.context.system.scheduler.scheduleOnce(10 seconds, self, Disconnect)
  }

  private[this] def handleDisconnect(): Unit = {
    this.connectionActor ! CloseConnection
    self ! PoisonPill
  }

  private[this] def handleHandshakeSuccess(success: InternalHandshakeSuccess): Unit = {
    val InternalHandshakeSuccess(
    client,
    clientVersion,
    HandshakeSuccess(modelStoreActor, operationStoreActor, identityActor, presenceActor, chatLookupActor),
    cb) = success

    this.client = client
    this.clientVersion = clientVersion
    this.modelStoreActor = modelStoreActor
    this.operationStoreActor = operationStoreActor
    this.identityServiceActor = identityActor
    this.presenceServiceActor = presenceActor
    this.chatLookupActor = chatLookupActor
    log.debug(s"$domainId: Sending handshake response to client")

    this.identityCacheManager = context.actorOf(IdentityCacheManager.props(self, identityActor, requestTimeout))

    // FIXME Protocol Config??
    cb.reply(HandshakeResponseMessage(success = true, None, retryOk = true, this.domainId.namespace, this.domainId.domainId, None))

    this.messageHandler = handleAuthenticationMessage
    context.become(receiveWhileAuthenticating)
  }

  //
  // Authentication
  //

  private[this] def authenticate(requestMessage: AuthenticationRequestMessage, cb: ReplyCallback): Unit = {
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
        val authRequest = AuthenticationRequest(
          domainId,
          self,
          remoteHost.toString,
          this.client,
          this.clientVersion,
          userAgent,
          authCredentials)

        // FIXME if authentication fails we should probably stop the actor
        // and or shut down the connection?
        (this.domainRegion ? authRequest).mapResponse[AuthenticationResponse] onComplete {
          case Success(AuthenticationSuccess(session, reconnectToken)) =>
            getPresenceAfterAuth(session, reconnectToken, cb)
          case Success(AuthenticationFailure) =>
            cb.reply(AuthenticationResponseMessage().withFailure(AuthFailureData("")))
          case Failure(cause) =>
            log.error(cause, s"Error authenticating user for domain $domainId")
            cb.reply(AuthenticationResponseMessage().withFailure(AuthFailureData("")))
        }
      case None =>
        log.error("Invalid authentication message: {}", requestMessage)
        cb.reply(AuthenticationResponseMessage().withFailure(AuthFailureData("")))
    }
  }

  private[this] def getPresenceAfterAuth(session: DomainUserSessionId, reconnectToken: Option[String], cb: ReplyCallback): Unit = {
    (for {
      // TODO implement a method that just gets one.
      presence <- (this.presenceServiceActor ? GetPresenceRequest(List(session.userId)))
        .mapTo[GetPresenceResponse]
        .map(_.presence)
        .flatMap {
          case first :: _ => Future.successful(first)
          case _ => Future.failed(EntityNotFoundException())
        }
      user <- (this.identityServiceActor ? GetUserRequest(session.userId)).mapTo[GetUserResponse].map(_.user)
    } yield {
      self ! InternalAuthSuccess(user, session, reconnectToken, presence, cb)
    }).recover {
      case cause =>
        log.error(cause, "Error getting user data after successful authentication")
        cb.reply(AuthenticationResponseMessage().withFailure(AuthFailureData("")))
    }
  }

  private[this] def handleAuthenticationSuccess(message: InternalAuthSuccess): Unit = {
    val InternalAuthSuccess(user, session, reconnectToken, presence, cb) = message
    this.sessionId = session.sessionId
    this.reconnectToken = reconnectToken
    this.modelClient = context.actorOf(ModelClientActor.props(domainId, session, modelStoreActor, requestTimeout, modelSyncInterval))
    this.userClient = context.actorOf(IdentityClientActor.props(identityServiceActor))
    this.chatClient = context.actorOf(ChatClientActor.props(domainId, chatLookupActor, session, requestTimeout))
    this.activityClient = context.actorOf(ActivityClientActor.props(
      ActivityActorSharding.shardRegion(this.context.system),
      domainId,
      session))
    this.presenceClient = context.actorOf(PresenceClientActor.props(presenceServiceActor, session))
    this.historyClient = context.actorOf(HistoricModelClientActor.props(session, domainId, modelStoreActor, operationStoreActor));
    this.messageHandler = handleMessagesWhenAuthenticated

    val response = AuthenticationResponseMessage().withSuccess(AuthSuccessData(
      Some(ImplicitMessageConversions.mapDomainUser(user)),
      session.sessionId,
      this.reconnectToken.getOrElse(""),
      JsonProtoConverter.jValueMapToValueMap(presence.state)))
    cb.reply(response)

    context.become(receiveWhileAuthenticated)
  }

  //
  // Incoming / Outgoing Messages
  //

  private[this] def onOutgoingMessage(message: GeneratedMessage with NormalMessage): Unit = {
    protocolConnection.send(message)
  }

  private[this] def onOutgoingRequest(message: GeneratedMessage with RequestMessage): Unit = {
    val askingActor = sender
    val f = protocolConnection.request(message)
    f.mapTo[ResponseMessage] onComplete {
      case Success(response) =>
        askingActor ! response
      case Failure(cause) =>
        log.error("Error processing a response message", cause)
        this.protocolConnection.send(ErrorMessage("invalid_response", "Error processing a response", Map()))
        this.connectionActor ! PoisonPill
        this.onConnectionClosed()
    }
  }

  private[this] def onMessageReceived(message: MessageReceived): Unit = {
    message match {
      case MessageReceived(_: ModelMessage) =>
        modelClient.forward(message)
      case MessageReceived(_: ActivityMessage) =>
        activityClient.forward(message)
      case MessageReceived(_: PresenceMessage) =>
        presenceClient.forward(message)
      case MessageReceived(_: ChatMessage) =>
        chatClient.forward(message)
      case _: Any =>
      // TODO send an error back
    }
  }

  private[this] def onRequestReceived(message: RequestReceived): Unit = {
    message match {
      case RequestReceived(_: ModelMessage, _) =>
        modelClient.forward(message)
      case RequestReceived(_: IdentityMessage, _) =>
        userClient.forward(message)
      case RequestReceived(_: ActivityMessage, _) =>
        activityClient.forward(message)
      case RequestReceived(_: PresenceMessage, _) =>
        presenceClient.forward(message)
      case RequestReceived(_: ChatMessage, _) =>
        chatClient.forward(message)
      case RequestReceived(_: HistoricalMessage, _) =>
        historyClient.forward(message)
      case RequestReceived(x: PermissionRequest, _) =>
        val idType = x.idType
        if (idType == PermissionType.CHAT) {
          chatClient.forward(message)
        }
      case message: Any =>
      // TODO send an error back
    }
  }

  //
  // Error handling
  //

  private[this] def onConnectionClosed(): Unit = {
    log.debug(s"$domainId: Sending disconnect to domain and stopping: $sessionId")
    domainRegion ! ClientDisconnected(domainId, self)

    // TODO we may want to keep this client alive to smooth over reconnect in the future.
    self ! PoisonPill
  }

  private[this] def onConnectionError(cause: Throwable): Unit = {
    log.debug(s"$domainId: Connection Error for: $sessionId - ${cause.getMessage}")
    domainRegion ! ClientDisconnected(domainId, self)
    this.disconnect()
  }

  private[this] def invalidMessage(message: Any): Unit = {
    log.error(s"$domainId: Invalid message: '$message'")
    this.disconnect()
  }

  private[this] def domainDeleted(): Unit = {
    log.error(s"$domainId: Domain deleted shutting down")
    this.disconnect()
  }

  override def postStop(): Unit = {
    log.debug(s"ClientActor($domainId/${this.sessionId}): Stopped")
    if (!handshakeTimeoutTask.isCancelled) {
      handshakeTimeoutTask.cancel()
    }
    Option(protocolConnection).foreach(_.dispose())
  }
}

private[realtime] object ClientActor {
  def props(domainFqn: DomainId,
            protocolConfig: ProtocolConfiguration,
            remoteHost: RemoteAddress,
            userAgent: String,
            modelSyncInterval: FiniteDuration): Props = Props(
    new ClientActor(
      domainFqn,
      protocolConfig,
      remoteHost,
      userAgent,
      modelSyncInterval))

  sealed trait ClientActorMessage extends CborSerializable

  case class SendUnprocessedMessage(message: ConvergenceMessage) extends ClientActorMessage

  case class SendProcessedMessage(message: ConvergenceMessage) extends ClientActorMessage

  case class InternalAuthSuccess(user: DomainUser,
                                 session: DomainUserSessionId,
                                 reconnectToken: Option[String],
                                 presence: UserPresence,
                                 cb: ReplyCallback) extends ClientActorMessage

  case class InternalHandshakeSuccess(client: String,
                                      clientVersion: String,
                                      handshakeSuccess: HandshakeSuccess,
                                      cb: ReplyCallback) extends ClientActorMessage

  case object Disconnect extends ClientActorMessage

}


