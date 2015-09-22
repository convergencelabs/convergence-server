package com.convergencelabs.server.frontend.realtime

import scala.concurrent.duration.DurationInt
import scala.language.postfixOps
import scala.util.Failure
import scala.util.Success
import com.convergencelabs.server.ProtocolConfiguration
import com.convergencelabs.server.domain.DomainFqn
import com.convergencelabs.server.domain.HandshakeFailure
import com.convergencelabs.server.domain.HandshakeRequest
import com.convergencelabs.server.domain.HandshakeSuccess
import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.Props
import akka.actor.actorRef2Scala
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.Promise
import scala.util.Success
import com.convergencelabs.server.frontend.realtime.proto.IncomingModelRequestMessage
import com.convergencelabs.server.frontend.realtime.proto.IncomingModelMessage
import com.convergencelabs.server.frontend.realtime.proto.IncomingProtocolNormalMessage
import com.convergencelabs.server.frontend.realtime.proto.IncomingProtocolResponseMessage
import com.convergencelabs.server.frontend.realtime.proto.IncomingProtocolRequestMessage
import com.convergencelabs.server.frontend.realtime.proto.OutgoingProtocolResponseMessage
import com.convergencelabs.server.frontend.realtime.proto.OutgoingProtocolNormalMessage
import com.convergencelabs.server.frontend.realtime.proto.OutgoingProtocolRequestMessage
import com.convergencelabs.server.frontend.realtime.proto.HandshakeRequestMessage
import com.convergencelabs.server.frontend.realtime.proto.HandshakeRequestMessage
import com.convergencelabs.server.frontend.realtime.proto.OutgoingProtocolResponseMessage
import com.convergencelabs.server.domain.HandshakeResponse
import com.convergencelabs.server.frontend.realtime.proto.HandshakeResponseMessage

object ClientActor {
  def props(
    domainManager: ActorRef,
    socket: ConvergenceServerSocket,
    domainFqn: DomainFqn,
    protocolConfig: ProtocolConfiguration): Props = Props(
    new ClientActor(domainManager, socket, domainFqn, protocolConfig))
}

class ClientActor(
    private[this] val domainManager: ActorRef,
    private[this] val socket: ConvergenceServerSocket,
    private[this] val domainFqn: DomainFqn,
    private[this] val protocolConfig: ProtocolConfiguration) extends Actor with ActorLogging {

  private[this] val connectionManager = context.parent

  implicit val ec = context.dispatcher

  val connection = new ProtocolConnection(
    socket,
    protocolConfig,
    context.system.scheduler,
    context.dispatcher,
    handleConnectionEvent)

  val modelClient = new ModelClient(
    self,
    self, // FIXME
    ec,
    connection)

  var domainActor: ActorRef = _

  
  
  def receive = receiveWhileHandshaking

  def receiveWhileHandshaking: Receive = {
    case RequestReceived(message, replyPromise) if message.isInstanceOf[HandshakeRequestMessage] => {
      handshake(message.asInstanceOf[HandshakeRequestMessage], replyPromise)
    }
    case x => unhandled(x)
  }
  
  def handshake(request: HandshakeRequestMessage, reply: Promise[OutgoingProtocolResponseMessage]): Unit = {
    // FIXME hardcoded
    implicit val timeout = Timeout(5 seconds)
    val f = domainManager ? HandshakeRequest(domainFqn, self, request.reconnect, request.reconnectToken)
    f.mapTo[HandshakeResponse] onComplete {
      case Success(HandshakeSuccess(sessionId, resonnectToken, domainActor, modelManagerActor)) => {
        this.domainActor = domainActor
        context.become(receiveWhileHandshook)
        reply.success(HandshakeResponseMessage(true, None, sessionId, resonnectToken))
      }
      case Success(HandshakeFailure(reason, retry)) => {

      }
      case Failure(cause) => {}
    }
  }

  def receiveWhileHandshook: Receive = {
    case message: OutgoingProtocolNormalMessage => {
      connection.send(message)
    }
    case message: OutgoingProtocolRequestMessage => {
      val askingActor = sender()
      val f = connection.request(message)
      
      f.mapTo[IncomingProtocolResponseMessage] onComplete { 
        case Success(response) => {
          askingActor ! response          
        }
        case Failure(cause) => {
        }
      }
    }
    case x => unhandled(x)
  }

  

  private def handleConnectionEvent: PartialFunction[ConnectionEvent, Unit] = {
    case MessageReceived(message) => onMessageReceived(message)
    case RequestReceived(message, replyPromise) => onRequestReceived(message, replyPromise)
    case ConnectionClosed() => onConnectionClosed()
    case ConnectionDropped() => onConnectionDropped()
    case ConnectionError(message) => onConnectionError(message)
  }

  private def onMessageReceived(message: IncomingProtocolNormalMessage): Unit = {
    message match {
      case modelMessage: IncomingModelMessage => modelClient.onMessageReceived(modelMessage)
      case _ => ???
    }
  }

  private def onRequestReceived(message: IncomingProtocolRequestMessage, replyPromise: Promise[OutgoingProtocolResponseMessage]): Unit = {
    message match {
      case modelMessage: IncomingModelRequestMessage => modelClient.onRequestReceived(modelMessage, replyPromise)
      case _ => ???
    }
  }

  private def onConnectionClosed(): Unit = {

  }

  private def onConnectionDropped(): Unit = {

  }

  private def onConnectionError(message: String): Unit = {

  }
}