package com.convergencelabs.server.frontend.realtime

import akka.actor.ActorLogging
import akka.actor.Actor
import akka.actor.Props
import akka.actor.ActorRef
import com.convergencelabs.server.domain.DomainFqn
import com.convergencelabs.server.ProtocolConfiguration
import com.convergencelabs.server.frontend.realtime.proto.ProtocolMessage
import scala.concurrent.Promise
import com.convergencelabs.server.domain.HandshakeRequest
import akka.actor.ActorSelection
import akka.pattern.{ ask, pipe }
import akka.util.Timeout
import scala.concurrent.duration._
import com.convergencelabs.server.domain.HandshakeSuccess
import scala.util.Success
import com.convergencelabs.server.domain.HandshakeFailure
import scala.util.Failure
import language.postfixOps

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

  def handshaked(): Unit = {
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

  private def onMessageReceived(message: ProtocolMessage): Unit = {
    message match {
      case _ => {}
    }
  }

  private def onRequestReceived(message: ProtocolMessage, replyPromise: Promise[ProtocolMessage]): Unit = {

  }

  private def onConnectionClosed(): Unit = {

  }

  private def onConnectionDropped(): Unit = {

  }

  private def onConnectionError(message: String): Unit = {

  }
}