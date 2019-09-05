package com.convergencelabs.server.api.realtime

import scala.concurrent.Future
import scala.language.postfixOps
import scala.util.Failure
import scala.util.Success

import com.convergencelabs.server.ProtocolConfiguration
import com.convergencelabs.server.datastore.EntityNotFoundException
import com.convergencelabs.server.db.provision.DomainProvisionerActor.DomainDeleted
import com.convergencelabs.server.db.provision.DomainProvisionerActor.domainTopic
import com.convergencelabs.server.domain.AnonymousAuthRequest
import com.convergencelabs.server.domain.AuthenticationFailure
import com.convergencelabs.server.domain.AuthenticationRequest
import com.convergencelabs.server.domain.AuthenticationResponse
import com.convergencelabs.server.domain.AuthenticationSuccess
import com.convergencelabs.server.domain.ClientDisconnected
import com.convergencelabs.server.domain.DomainActorSharding
import com.convergencelabs.server.domain.DomainId
import com.convergencelabs.server.domain.DomainUser
import com.convergencelabs.server.domain.DomainUserSessionId
import com.convergencelabs.server.domain.GetUserByUsername
import com.convergencelabs.server.domain.HandshakeFailureException
import com.convergencelabs.server.domain.HandshakeRequest
import com.convergencelabs.server.domain.HandshakeSuccess
import com.convergencelabs.server.domain.JwtAuthRequest
import com.convergencelabs.server.domain.PasswordAuthRequest
import com.convergencelabs.server.domain.ReconnectTokenAuthRequest
import com.convergencelabs.server.domain.activity.ActivityActorSharding
import com.convergencelabs.server.domain.presence.PresenceRequest
import com.convergencelabs.server.domain.presence.UserPresence
import com.convergencelabs.server.util.concurrent.AskFuture
import scala.concurrent.duration._

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.PoisonPill
import akka.actor.Props
import akka.actor.actorRef2Scala
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.Subscribe
import akka.cluster.pubsub.DistributedPubSubMediator.SubscribeAck
import akka.http.scaladsl.model.RemoteAddress
import akka.util.Timeout
import io.convergence.proto.Activity
import io.convergence.proto.Authentication
import io.convergence.proto.Chat
import io.convergence.proto.Historical
import io.convergence.proto.Identity
import io.convergence.proto.Model
import io.convergence.proto.Normal
import io.convergence.proto.PermissionRequest
import io.convergence.proto.Presence
import io.convergence.proto.Request
import io.convergence.proto.Response
import io.convergence.proto.authentication.AnonymousAuthRequestMessage
import io.convergence.proto.authentication.AuthFailure
import io.convergence.proto.authentication.AuthSuccess
import io.convergence.proto.authentication.AuthenticationRequestMessage
import io.convergence.proto.authentication.AuthenticationResponseMessage
import io.convergence.proto.authentication.JwtAuthRequestMessage
import io.convergence.proto.authentication.PasswordAuthRequestMessage
import io.convergence.proto.authentication.ReconnectTokenAuthRequestMessage
import io.convergence.proto.common.ErrorMessage
import io.convergence.proto.connection.HandshakeRequestMessage
import io.convergence.proto.connection.HandshakeResponseMessage
import io.convergence.proto.connection.HandshakeResponseMessage.ErrorData
import io.convergence.proto.message.ConvergenceMessage
import io.convergence.proto.permissions.PermissionType
import scalapb.GeneratedMessage
import scala.concurrent.duration.FiniteDuration
import java.util.concurrent.TimeUnit

object ClientActor {
  def props(
    domainFqn:      DomainId,
    protocolConfig: ProtocolConfiguration,
    remoteHost:     RemoteAddress,
    userAgent:      String): Props = Props(
    new ClientActor(
      domainFqn,
      protocolConfig,
      remoteHost,
      userAgent))
}

class ClientActor(
  private[this] val domainFqn:      DomainId,
  private[this] val protocolConfig: ProtocolConfiguration,
  private[this] val remoteHost:     RemoteAddress,
  private[this] val userAgent:      String)
  extends Actor with ActorLogging {

  import akka.pattern.ask

  type MessageHandler = PartialFunction[ProtocolMessageEvent, Unit]

  // FIXME hard-coded (used for auth and handshake)
  private[this] implicit val requestTimeout = Timeout(protocolConfig.defaultRequestTimeout)
  private[this] implicit val ec = context.dispatcher

  private[this] var connectionActor: ActorRef = _

  val mediator = DistributedPubSub(context.system).mediator
  mediator ! Subscribe(domainTopic(domainFqn), self)

  // FIXME this should probably be for handshake and auth.
  private[this] val handshakeTimeoutTask =
    context.system.scheduler.scheduleOnce(protocolConfig.handshakeTimeout) {
      log.debug(s"${domainFqn}: Client handshaked timeout")
      Option(connectionActor) match {
        case Some(connection) => connection ! CloseConnection
        case None             =>
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
        case _         => this.protocolConnection.serializeAndSend(convergenceMessage)
      }
    case SendProcessedMessage(convergenceMessage) =>
      this.protocolConnection.serializeAndSend(convergenceMessage)
    case message: GeneratedMessage with Normal =>
      onOutgoingMessage(message)
    case message: GeneratedMessage with Request =>
      onOutgoingRequest(message)
    case WebSocketClosed =>
      log.debug(s"${domainFqn}: WebSocketClosed for session: ${sessionId}")
      onConnectionClosed()
    case WebSocketError(cause) =>
      onConnectionError(cause)
    case SubscribeAck(_) =>
    // no-op
    case DomainDeleted(domainFqn) =>
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

  private[this] val receiveAuthentiationSuccess: Receive = {
    case authSuccess: InternalAuthSuccess =>
      handleAuthenticationSuccess(authSuccess)
  }

  private[this] val receiveWhileAuthenticating =
    receiveAuthentiationSuccess orElse
      receiveCommon

  private[this] val receiveWhileAuthenticated =
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
    case RequestReceived(message, replyPromise) if message.isInstanceOf[GeneratedMessage with Request with Authentication] => invalidMessage(message)

    case message: MessageReceived => onMessageReceived(message)
    case message: RequestReceived => onRequestReceived(message)
  }

  //
  // Handshaking
  //

  private[this] def handshake(request: HandshakeRequestMessage, cb: ReplyCallback): Unit = {
    val canceled = handshakeTimeoutTask.cancel()
    if (canceled) {
      log.debug(s"${domainFqn}: Handhsaking with DomainActor")
      val future = domainRegion ? HandshakeRequest(domainFqn, self, request.reconnect, request.reconnectToken)

      future.mapResponse[HandshakeSuccess] onComplete {
        case Success(success) => {
          log.debug(s"${domainFqn}: Handhsake success")
          self ! InternalHandshakeSuccess(request.client, request.clientVersion, success, cb)
        }
        case Failure(HandshakeFailureException(code, details)) => {
          log.debug(s"${domainFqn}: Handshake failure: {code: '${code}', details: '${details}'}")
          cb.reply(HandshakeResponseMessage(false, Some(ErrorData(code, details)), false))
          this.disconnect()
        }
        case Failure(cause) => {
          log.error(cause, s"${domainFqn}: Error handshaking with DomainActor")
          cb.reply(HandshakeResponseMessage(false, Some(ErrorData("unknown", "uknown error")), true))
          this.disconnect()
        }
      }
    } else {
      log.debug(s"${domainFqn}: Not handhsaking with domain because handshake timeout occured")
    }
  }

  // TODO add an optional message to send to the client.
  private[this] def disconnect(): Unit = {
    // TODO we do this to allow outgoing messages to be flushed
    // What we SHOULD do is send a message to the protocol connection and then have it shut down
    // when it processes that message. That would cause it to flush any messages in the queue 
    // before shutting down.
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
    log.debug(s"${domainFqn}: Sending handshake response to client")

    this.identityCacheManager = context.actorOf(IdentityCacheManager.props(self, identityActor, requestTimeout))

    // FIXME Protocol Config??
    cb.reply(HandshakeResponseMessage(true, None, true, this.domainFqn.namespace, this.domainFqn.domainId, None))

    this.messageHandler = handleAuthentationMessage
    context.become(receiveWhileAuthenticating)
  }

  //
  // Authentication
  //

  private[this] def authenticate(requestMessage: AuthenticationRequestMessage, cb: ReplyCallback): Unit = {
    (requestMessage.auth match {
      case AuthenticationRequestMessage.Auth.Password(PasswordAuthRequestMessage(username, password)) =>
        Some(PasswordAuthRequest(username, password))
      case AuthenticationRequestMessage.Auth.Jwt(JwtAuthRequestMessage(jwt)) =>
        Some(JwtAuthRequest(jwt))
      case AuthenticationRequestMessage.Auth.Reconnect(ReconnectTokenAuthRequestMessage(token)) =>
        Some(ReconnectTokenAuthRequest(token))
      case AuthenticationRequestMessage.Auth.Anonymous(AnonymousAuthRequestMessage(displayName)) =>
        Some(AnonymousAuthRequest(displayName))
      case AuthenticationRequestMessage.Auth.Empty =>
        None
    }) match {
      case Some(authCredentials) =>
        val authRequest = AuthenticationRequest(
          domainFqn,
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
            cb.reply(AuthenticationResponseMessage().withFailure(AuthFailure("")))
          case Failure(cause) =>
            log.error(cause, s"Error authenticating user for domain ${domainFqn}")
            cb.reply(AuthenticationResponseMessage().withFailure(AuthFailure("")))
        }
      case None =>
        log.error("Invalid authentication message: {}", requestMessage)
        cb.reply(AuthenticationResponseMessage().withFailure(AuthFailure("")))
    }
  }

  private[this] def getPresenceAfterAuth(session: DomainUserSessionId, reconnectToken: Option[String], cb: ReplyCallback) {
    (for {
      // TODO implement a method that just gets one.
      presence <- (this.presenceServiceActor ? PresenceRequest(List(session.userId)))
        .mapTo[List[UserPresence]]
        .flatMap { p =>
          p match {
            case first :: nil => Future.successful(first)
            case _            => Future.failed(EntityNotFoundException())
          }
        }
      user <- (this.identityServiceActor ? GetUserByUsername(session.userId)).mapTo[DomainUser]
    } yield {
      self ! InternalAuthSuccess(user, session, reconnectToken, presence, cb)
    }).recover {
      case cause =>
        log.error(cause, "Error getting user data after successful authentication")
        cb.reply(AuthenticationResponseMessage().withFailure(AuthFailure("")))
    }
  }

  private[this] def handleAuthenticationSuccess(message: InternalAuthSuccess): Unit = {
    val InternalAuthSuccess(user, session, reconnectToken, presence, cb) = message
    this.sessionId = session.sessionId
    this.reconnectToken = reconnectToken;
    this.modelClient = context.actorOf(ModelClientActor.props(domainFqn, session, modelStoreActor, requestTimeout))
    this.userClient = context.actorOf(IdentityClientActor.props(identityServiceActor))
    this.chatClient = context.actorOf(ChatClientActor.props(domainFqn, chatLookupActor, session, requestTimeout))
    this.activityClient = context.actorOf(ActivityClientActor.props(
      ActivityActorSharding.shardRegion(this.context.system),
      domainFqn,
      session))
    this.presenceClient = context.actorOf(PresenceClientActor.props(presenceServiceActor, session))
    this.historyClient = context.actorOf(HistoricModelClientActor.props(session, domainFqn, modelStoreActor, operationStoreActor));
    this.messageHandler = handleMessagesWhenAuthenticated

    val response = AuthenticationResponseMessage().withSuccess(AuthSuccess(
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

  private[this] def onOutgoingMessage(message: GeneratedMessage with Normal): Unit = {
    protocolConnection.send(message)
  }

  private[this] def onOutgoingRequest(message: GeneratedMessage with Request): Unit = {
    val askingActor = sender
    val f = protocolConnection.request(message)
    f.mapTo[Response] onComplete {
      case Success(response) =>
        askingActor ! response
      case Failure(cause) =>
        this.protocolConnection.send(ErrorMessage("invalid_response", "Error processing a response", Map()))
        this.connectionActor ! PoisonPill
        this.onConnectionClosed()
    }
  }

  private[this] def onMessageReceived(message: MessageReceived): Unit = {
    message match {
      case MessageReceived(x: Model) =>
        modelClient.forward(message)
      case MessageReceived(x: Activity) =>
        activityClient.forward(message)
      case MessageReceived(x: Presence) =>
        presenceClient.forward(message)
      case MessageReceived(x: Chat) =>
        chatClient.forward(message)
      case message: Any =>
      // TODO send an error back
    }
  }

  private[this] def onRequestReceived(message: RequestReceived): Unit = {
    message match {
      case RequestReceived(x: Model, _) =>
        modelClient.forward(message)
      case RequestReceived(x: Identity, _) =>
        userClient.forward(message)
      case RequestReceived(x: Activity, _) =>
        activityClient.forward(message)
      case RequestReceived(x: Presence, _) =>
        presenceClient.forward(message)
      case RequestReceived(x: Chat, _) =>
        chatClient.forward(message)
      case RequestReceived(x: Historical, _) =>
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
    log.debug(s"${domainFqn}: Sending disconnect to domain and stopping: ${sessionId}")
    domainRegion ! ClientDisconnected(domainFqn, self)
    
    // TODO we may want to keep this client alive to smooth over reconnect in the future.
    self ! PoisonPill
  }

  private[this] def onConnectionError(cause: Throwable): Unit = {
    log.debug(s"${domainFqn}: Connection Error for: ${sessionId} - " + cause.getMessage)
    domainRegion ! ClientDisconnected(domainFqn, self)
    this.disconnect()
  }

  private[this] def invalidMessage(message: Any): Unit = {
    log.error(s"${domainFqn}: Invalid message: '${message}'")
    this.disconnect()
  }

  private[this] def domainDeleted(): Unit = {
    log.error(s"${domainFqn}: Domain deleted shutting down")
    this.disconnect()
  }

  override def postStop(): Unit = {
    log.debug(s"ClientActor(${domainFqn}/${this.sessionId}): Stopped")
    if (!handshakeTimeoutTask.isCancelled) {
      handshakeTimeoutTask.cancel()
    }
    Option(protocolConnection).map(_.dispose())
  }
}

case class SendUnprocessedMessage(message: ConvergenceMessage)
case class SendProcessedMessage(message: ConvergenceMessage)
case class InternalAuthSuccess(user: DomainUser, session: DomainUserSessionId, reconnectToken: Option[String], presence: UserPresence, cb: ReplyCallback)
case class InternalHandshakeSuccess(client: String, clientVersion: String, handshakeSuccess: HandshakeSuccess, cb: ReplyCallback)
case object Disconnect
