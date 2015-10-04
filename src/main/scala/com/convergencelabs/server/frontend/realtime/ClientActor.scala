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
import com.convergencelabs.server.frontend.realtime.proto.ErrorData
import com.convergencelabs.server.domain.model.RealtimeModelClientMessage

object ClientActor {
  def props(
    domainManager: ActorRef,
    connection: ProtocolConnection,
    domainFqn: DomainFqn): Props = Props(
    new ClientActor(domainManager, connection, domainFqn))
}

class ClientActor(
  private[this] val domainManager: ActorRef,
  private[this] val connection: ProtocolConnection,
  private[this] val domainFqn: DomainFqn)
    extends Actor with ActorLogging {

  private[this] val connectionManager = context.parent

  implicit val ec = context.dispatcher

  connection.eventHandler = { case event => self ! event }

  val modelClient = new ModelClient(
    this,
    self, // FIXME
    ec)

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
      case Success(HandshakeSuccess(sessionId, reconnectToken, domainActor, modelManagerActor)) => {
        this.domainActor = domainActor
        context.become(receiveWhileHandshook)
        reply.success(HandshakeResponseMessage(true, None, Some(sessionId), Some(reconnectToken)))
      }
      case Success(HandshakeFailure(code, details)) => {
        reply.success(HandshakeResponseMessage(false, Some(ErrorData(code, details)), None, None))
      }
      case Failure(cause) => {
        // FIXME handle this better.  Handle timeout vs. class cast vs. whatever?
        reply.success(HandshakeResponseMessage(false, Some(ErrorData("unknown", "uknown error")), None, None))
      }
    }
  }

  def receiveWhileHandshook: Receive = {
    case message: RealtimeModelClientMessage => modelClient.onOutgoingModelMessage(message, sender())
    
    case MessageReceived(message) => onMessageReceived(message)
    case RequestReceived(message, replyPromise) => onRequestReceived(message, replyPromise)
    case ConnectionClosed() => onConnectionClosed()
    case ConnectionDropped() => onConnectionDropped()
    case ConnectionError(message) => onConnectionError(message)
    case x => unhandled(x)
  }

  private[realtime] def send(message: OutgoingProtocolNormalMessage): Unit = {
    connection.send(message)
  }

  private[realtime] def request(message: OutgoingProtocolRequestMessage): Unit = {
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
    context.stop(self)
  }

  private def onConnectionDropped(): Unit = {
    context.stop(self)
  }

  private def onConnectionError(message: String): Unit = {
    context.stop(self)
  }
}