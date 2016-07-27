package com.convergencelabs.server.frontend.realtime

import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.FiniteDuration
import scala.language.postfixOps
import scala.util.Failure
import scala.util.Success
import com.convergencelabs.server.domain.AuthenticationError
import com.convergencelabs.server.domain.AuthenticationFailure
import com.convergencelabs.server.domain.AuthenticationResponse
import com.convergencelabs.server.domain.AuthenticationSuccess
import com.convergencelabs.server.domain.ClientDisconnected
import com.convergencelabs.server.domain.DomainFqn
import com.convergencelabs.server.domain.HandshakeFailure
import com.convergencelabs.server.domain.HandshakeRequest
import com.convergencelabs.server.domain.HandshakeResponse
import com.convergencelabs.server.domain.HandshakeSuccess
import com.convergencelabs.server.domain.PasswordAuthRequest
import com.convergencelabs.server.domain.TokenAuthRequest
import com.convergencelabs.server.util.concurrent.AskFuture
import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.Props
import akka.actor.actorRef2Scala
import akka.pattern.ask
import akka.util.Timeout
import com.convergencelabs.server.domain.HandshakeSuccess
import scala.util.Failure
import com.convergencelabs.server.ProtocolConfiguration
import akka.actor.PoisonPill
import com.convergencelabs.server.domain.model.SessionKey

object ClientActor {
  def props(
    domainManager: ActorRef,
    domainFqn: DomainFqn,
    protocolConfig: ProtocolConfiguration): Props = Props(
    new ClientActor(
      domainManager,
      domainFqn,
      protocolConfig))
}

class ClientActor(
  private[this] val domainManager: ActorRef,
  private[this] val domainFqn: DomainFqn,
  private[this] val protocolConfig: ProtocolConfiguration)
    extends Actor with ActorLogging {

  type MessageHandler = PartialFunction[ProtocolMessageEvent, Unit]

  // FIXME hard-coded (used for auth and handshake)
  private[this] implicit val requestTimeout = Timeout(5 seconds)
  private[this] implicit val ec = context.dispatcher

  private[this] var connectionActor: ActorRef = _

  // FIXME this should probably be for handshake and auth.
  private[this] val handshakeTimeoutTask =
    context.system.scheduler.scheduleOnce(protocolConfig.handshakeTimeout) {
      log.debug("Client handshaked timeout")
      connectionActor ! CloseConnection
      context.stop(self)
    }

  private[this] val connectionManager = context.parent

  private[this] var modelClient: ActorRef = _
  private[this] var userClient: ActorRef = _
  private[this] var activityClient: ActorRef = _

  private[this] var domainActor: Option[ActorRef] = None
  private[this] var modelManagerActor: ActorRef = _
  private[this] var userServiceActor: ActorRef = _
  private[this] var activityServiceActor: ActorRef = _
  private[this] var sessionId: String = _

  private[this] var protocolConnection: ProtocolConnection = _

  def receive: Receive = receiveWhileConnecting

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
    case x: Any =>
      invalidMessage(x)
  }

  private[this] def receiveIncomingTextMessage: Receive = {
    case IncomingTextMessage(message) =>
      this.protocolConnection.onIncomingMessage(message) match {
        case Success(Some(event)) =>
          messageHandler(event)
        case Success(None) =>
        // No Op
        case Failure(cause) =>
          invalidMessage(cause)
      }
  }

  private[this] def receiveOutgoing: Receive = {
    case message: OutgoingProtocolNormalMessage => onOutgoingMessage(message)
    case message: OutgoingProtocolRequestMessage => onOutgoingRequest(message)
  }

  private[this] def receiveCommon: Receive = {
    case WebSocketClosed => onConnectionClosed()
    case WebSocketError(cause) => onConnectionError(cause)
    case x: Any => invalidMessage(x)
  }

  private[this] val receiveHandshakeSuccess: Receive = {
    case handshakeSuccess: InternalHandshakeSuccess =>
      handleHandshakeSuccess(handshakeSuccess)
  }

  private[this] val receiveWhileHandshaking =
    receiveHandshakeSuccess orElse
      receiveIncomingTextMessage orElse
      receiveCommon

  private[this] val receiveAuthentiationSuccess: Receive = {
    case authSuccess: InternalAuthSuccess =>
      handleAuthenticationSuccess(authSuccess)

  }

  private[this] val receiveWhileAuthenticating =
    receiveAuthentiationSuccess orElse
      receiveIncomingTextMessage orElse
      receiveCommon

  private[this] val receiveWhileAuthenticated =
    receiveIncomingTextMessage orElse
      receiveOutgoing orElse
      receiveCommon

  private[this] var messageHandler: MessageHandler = handleHandshakeMessage

  private[this] def handleHandshakeMessage: MessageHandler = {
    case RequestReceived(message, replyCallback) if message.isInstanceOf[HandshakeRequestMessage] => {
      handshake(message.asInstanceOf[HandshakeRequestMessage], replyCallback)
    }
    case x: Any => invalidMessage(x)
  }

  private[this] def handleAuthentationMessage: MessageHandler = {
    case RequestReceived(message, replyCallback) if message.isInstanceOf[AuthenticationRequestMessage] => {
      authenticate(message.asInstanceOf[AuthenticationRequestMessage], replyCallback)
    }
    case x: Any => invalidMessage(x)
  }

  private[this] def handleMessagesWhenAuthenticated: MessageHandler = {
    case RequestReceived(message, replyPromise) if message.isInstanceOf[HandshakeRequestMessage] => invalidMessage(message)
    case RequestReceived(message, replyPromise) if message.isInstanceOf[AuthenticationRequestMessage] => invalidMessage(message)

    case message: MessageReceived => onMessageReceived(message)
    case message: RequestReceived => onRequestReceived(message)
  }

  private[this] def authenticate(requestMessage: AuthenticationRequestMessage, cb: ReplyCallback): Unit = {
    val message = requestMessage match {
      case PasswordAuthRequestMessage(username, password) => PasswordAuthRequest(username, password)
      case TokenAuthRequestMessage(token) => TokenAuthRequest(token)
    }

    val future = domainActor.get ? message

    // FIXME if authentication fails we should probably stop the actor
    // and or shut down the connection?
    future.mapResponse[AuthenticationResponse] onComplete {
      case Success(AuthenticationSuccess(username, sk)) => {
        self ! InternalAuthSuccess(username, sk, cb)
      }
      case Success(AuthenticationFailure) => {
        cb.reply(AuthenticationResponseMessage(false, None, None))
      }
      case Success(AuthenticationError) => {
        cb.reply(AuthenticationResponseMessage(false, None, None)) // TODO do we want this to go back to the client as something else?
      }
      case Failure(cause) => {
        cb.reply(AuthenticationResponseMessage(false, None, None))
      }
    }
  }

  private[this] def handleAuthenticationSuccess(message: InternalAuthSuccess): Unit = {
    val InternalAuthSuccess(username, sk, cb) = message
    this.sessionId = sk.serialize();
    this.modelClient = context.actorOf(ModelClientActor.props(sk, modelManagerActor))
    this.userClient = context.actorOf(UserClientActor.props(userServiceActor))
    this.activityClient = context.actorOf(ActivityClientActor.props(activityServiceActor, sk))
    this.messageHandler = handleMessagesWhenAuthenticated
    cb.reply(AuthenticationResponseMessage(true, Some(username), Some(sk.serialize())))
    context.become(receiveWhileAuthenticated)
  }

  private[this] def handshake(request: HandshakeRequestMessage, cb: ReplyCallback): Unit = {
    log.debug("handhsaking with domain")
    val canceled = handshakeTimeoutTask.cancel()
    if (canceled) {
      val future = domainManager ? HandshakeRequest(domainFqn, self, request.r, request.k)
      future.mapResponse[HandshakeResponse] onComplete {
        case Success(success) if success.isInstanceOf[HandshakeSuccess] => {
          self ! InternalHandshakeSuccess(success.asInstanceOf[HandshakeSuccess], cb)
        }
        case Success(HandshakeFailure(code, details)) => {
          cb.reply(HandshakeResponseMessage(false, Some(ErrorData(code, details)), Some(true), None))
          this.connectionActor ! CloseConnection
          context.stop(self)
        }
        case Failure(cause) => {
          cb.reply(HandshakeResponseMessage(false, Some(ErrorData("unknown", "uknown error")), Some(true), None))
          this.connectionActor ! CloseConnection
          context.stop(self)
        }
      }
    }
  }

  private[this] def handleHandshakeSuccess(success: InternalHandshakeSuccess): Unit = {
    val InternalHandshakeSuccess(HandshakeSuccess(domainActor, modelManagerActor, userActor, activityActor),
      cb) = success
    this.domainActor = Some(domainActor)
    this.modelManagerActor = modelManagerActor
    this.userServiceActor = userActor
    this.activityServiceActor = activityActor
    cb.reply(HandshakeResponseMessage(true, None, None, Some(ProtocolConfigData(true))))
    this.messageHandler = handleAuthentationMessage
    context.become(receiveWhileAuthenticating)
  }

  private[this] def onOutgoingMessage(message: OutgoingProtocolNormalMessage): Unit = {
    protocolConnection.send(message)
  }

  private[this] def onOutgoingRequest(message: OutgoingProtocolRequestMessage): Unit = {
    val askingActor = sender
    val f = protocolConnection.request(message)
    f.mapTo[IncomingProtocolResponseMessage] onComplete {
      case Success(response) =>
        askingActor ! response
      case Failure(cause) =>
        this.protocolConnection.send(ErrorMessage("invalid_response", "Error processing a response"))
        this.connectionActor ! PoisonPill
        this.onConnectionClosed()
    }
  }

  private[this] def onMessageReceived(message: MessageReceived): Unit = {
    message match {
      case MessageReceived(x) if x.isInstanceOf[IncomingModelNormalMessage] => modelClient.forward(message)
      case MessageReceived(x) if x.isInstanceOf[IncomingActivityMessage] => activityClient.forward(message)
    }
  }

  private[this] def onRequestReceived(message: RequestReceived): Unit = {
    message match {
      case RequestReceived(x, _) if x.isInstanceOf[IncomingModelRequestMessage] =>
        modelClient.forward(message)
      case RequestReceived(x, _) if x.isInstanceOf[IncomingUserMessage] =>
        userClient.forward(message)
      case RequestReceived(x, _) if x.isInstanceOf[IncomingActivityMessage] =>
        activityClient.forward(message)
    }
  }

  private[this] def onConnectionClosed(): Unit = {
    log.info(s"Connection Closed: ${sessionId}")
    domainActor.foreach { _ ! ClientDisconnected(sessionId) }
    context.stop(self)
  }

  private[this] def onConnectionError(cause: Throwable): Unit = {
    log.debug("Connection Error: " + cause.getMessage)
    domainActor.foreach { _ ! ClientDisconnected(sessionId) }
    context.stop(self)
  }

  private[this] def invalidMessage(message: Any): Unit = {
    connectionActor ! CloseConnection
    context.stop(self)
  }

  override def postStop(): Unit = {
    if (!handshakeTimeoutTask.isCancelled) {
      handshakeTimeoutTask.cancel()
    }
    protocolConnection.dispose()
  }
}

case class InternalAuthSuccess(username: String, sk: SessionKey, cb: ReplyCallback)
case class InternalHandshakeSuccess(handshakeSuccess: HandshakeSuccess, cb: ReplyCallback)
