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
import com.convergencelabs.server.frontend.realtime.proto.ModelMessage

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
    case event: ProtocolMessageEvent => onMessageReceived(event)
    case ConnectionClosed() => onConnectionClosed()
    case ConnectionDropped() => onConnectionDropped()
    case ConnectionError(message) => onConnectionError(message)
  }

  private def onMessageReceived(event: ProtocolMessageEvent): Unit = {
    val message = event.message
    message match {
      // FIXME we effectively loose the fact that we have already narrowed this.
      // we are making an assumption in the handleMessage method.  Perhaps
      // the protocol message event needs to be a generic or something.
      case modelMessage: ModelMessage => modelClient.handleMessage(event)
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