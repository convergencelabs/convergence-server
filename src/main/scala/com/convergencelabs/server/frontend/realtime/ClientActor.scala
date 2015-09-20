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
import com.convergencelabs.server.frontend.realtime.proto.IncomingModelMessage
import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.Props
import akka.actor.actorRef2Scala
import akka.pattern.ask
import akka.util.Timeout
import com.convergencelabs.server.frontend.realtime.proto.IncomingModelMessage
import com.convergencelabs.server.frontend.realtime.proto.IncomingModelMessage
import com.convergencelabs.server.frontend.realtime.proto.ProtocolMessage
import scala.concurrent.Promise
import com.convergencelabs.server.frontend.realtime.proto.IncomingModelRequestMessage
import com.convergencelabs.server.frontend.realtime.proto.IncomingModelMessage
import com.convergencelabs.server.frontend.realtime.proto.IncomingProtocolMessage
import com.convergencelabs.server.frontend.realtime.proto.IncomingProtocolNormalMessage
import com.convergencelabs.server.frontend.realtime.proto.IncomingProtocolRequestMessage
import com.convergencelabs.server.frontend.realtime.proto.OutgoingProtocolResponseMessage

object ClientActor {
  def props(
    domainManager: ActorRef,
    socket: ConvergenceServerSocket,
    domainFqn: DomainFqn,
    sessionId: String,
    protocolConfig: ProtocolConfiguration): Props = Props(
    new ClientActor(domainManager, socket, domainFqn, sessionId, protocolConfig))
}

class ClientActor(
    private[this] val domainManager: ActorRef,
    private[this] val socket: ConvergenceServerSocket,
    private[this] val domainFqn: DomainFqn,
    private[this] val sessionId: String,
    private[this] val protocolConfig: ProtocolConfiguration) extends Actor with ActorLogging {

  private[this] val connectionManager = context.parent

  implicit val ec = context.dispatcher

  val connection = new ProtocolConnection(
    socket,
    sessionId,
    protocolConfig,
    context.system.scheduler,
    context.dispatcher,
    handleConnectionEvent)
  
  val modelClient = new ModelClient(
      self,
      self, // FIXME
      ec,
      connection)

  def handshaked(): Unit = {
    // FIXME hardcoded
    implicit val timeout = Timeout(5 seconds)
    val f = domainManager ? HandshakeRequest(domainFqn, sessionId, self)
    f onComplete {
      case _ => self ! _
    }
  }

  def receiveWhileHandshaking: Receive = {
    case Success(x) if x.isInstanceOf[HandshakeSuccess] => {}
    case Success(x) if x.isInstanceOf[HandshakeFailure] => {}
    case Failure(cause) => {}
    case x => unhandled(x)
  }

  def receive = receiveWhileHandshaking

  private def handleConnectionEvent: PartialFunction[ConnectionEvent, Unit] = {
    case MessageReceived(message) => onMessageReceived(message)
    case RequestReceived(message, replyPromise) => onRequestReceived(message, replyPromise)
    case ConnectionClosed() => onConnectionClosed()
    case ConnectionDropped() => onConnectionDropped()
    case ConnectionError(message) => onConnectionError(message)
  }

  private def onMessageReceived(message: IncomingProtocolNormalMessage): Unit = {
    message match {
      // FIXME we effectively loose the fact that we have already narrowed this.
      // we are making an assumption in the handleMessage method.  Perhaps
      // the protocol message event needs to be a generic or something.
      case modelMessage: IncomingModelMessage => modelClient.onMessageReceived(modelMessage)
      case _ => 
    }
  }

  private def onRequestReceived(message: IncomingProtocolRequestMessage, replyPromise: Promise[OutgoingProtocolResponseMessage]): Unit = {
    message match {
      // the protocol message event needs to be a generic or something.
      case modelMessage: IncomingModelRequestMessage => modelClient.onRequestReceived(modelMessage, replyPromise)
      case _ => 
    }
  }

  private def onConnectionClosed(): Unit = {

  }

  private def onConnectionDropped(): Unit = {

  }

  private def onConnectionError(message: String): Unit = {

  }
}