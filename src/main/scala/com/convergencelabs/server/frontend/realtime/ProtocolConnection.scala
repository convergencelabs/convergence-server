package com.convergencelabs.server.frontend.realtime

import com.convergencelabs.server.frontend.realtime.proto.ProtocolMessage
import com.convergencelabs.server.ProtocolConfiguration
import scala.collection.mutable
import scala.concurrent.Promise
import akka.actor.Cancellable
import com.convergencelabs.server.frontend.realtime.proto.MessageEnvelope
import org.json4s.jackson.Serialization
import org.json4s.jackson.Serialization.{ read, write }
import org.json4s.NoTypeHints
import scala.concurrent.Future
import scala.concurrent.ExecutionContext
import akka.actor.Scheduler
import scala.concurrent.duration.FiniteDuration
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.Duration
import java.util.concurrent.TimeoutException
import scala.util.Failure
import scala.util.Success
import com.convergencelabs.server.frontend.realtime.proto.OpCode
import com.convergencelabs.server.frontend.realtime.proto.IncomingProtocolMessage
import com.convergencelabs.server.frontend.realtime.proto.IncomingProtocolMessage
import com.convergencelabs.server.frontend.realtime.proto.IncomingProtocolRequestMessage
import com.convergencelabs.server.frontend.realtime.proto.IncomingProtocolNormalMessage
import com.convergencelabs.server.frontend.realtime.proto.OutgoingProtocolResponseMessage

object ProtocolConnection {
  object State extends Enumeration {
    val Connected, Connecting, Disconnected, Disconnecting, Interrupted = Value
  }
}


sealed trait ConnectionEvent

sealed trait ProtocolMessageEvent extends ConnectionEvent {
  def message: IncomingProtocolMessage
}

case class MessageReceived(message: IncomingProtocolNormalMessage) extends ProtocolMessageEvent
case class RequestReceived(message: IncomingProtocolRequestMessage, replyPromise: Promise[OutgoingProtocolResponseMessage]) extends ProtocolMessageEvent

case class ConnectionClosed() extends ConnectionEvent
case class ConnectionDropped() extends ConnectionEvent
case class ConnectionError(message: String) extends ConnectionEvent

class ProtocolConnection(
    private[this] var socket: ConvergenceServerSocket,
    private[this] val sessionId: String,
    private[this] val protocolConfig: ProtocolConfiguration,
    private[this] val scheduler: Scheduler,
    private[this] val ec: ExecutionContext,
    private[this] val connectionHandler: PartialFunction[ConnectionEvent, Unit]) {

  implicit val formats = Serialization.formats(NoTypeHints)

  val heartbeatHelper = new HearbeatHelper(
    5,
    10,
    scheduler,
    ec,
    handleHeartbeat)

  heartbeatHelper.start()

  socket.handler = {
    case SocketMessage(message) => onSocketMessage(message)
    case _: SocketClosed => onSocketClosed()
    case _: SocketDropped => onSocketDropped()
    case SocketError(message) => onSocketError(message)
  }

  import com.convergencelabs.server.frontend.realtime.ProtocolConnection.State._

  var nextRequestId = 0L

  val requestPromises = mutable.Map[Long, Promise[ProtocolMessage]]()
  val responseTimeoutTasks = mutable.Map[Long, Cancellable]()

  var state = Connected

  def send(message: ProtocolMessage): Unit = {
    sendMessage(OpCode.Normal, None, Some(message))
  }

  def request(message: ProtocolMessage)(implicit executor: ExecutionContext): Future[ProtocolMessage] = {
    val requestId = nextRequestId
    nextRequestId += 1

    val promise = Promise[ProtocolMessage]

    requestPromises.synchronized({
      requestPromises(requestId) = promise
    })

    val timeout = Duration.create(50, TimeUnit.MILLISECONDS)

    val timeoutFuture = scheduler.scheduleOnce(timeout)(() => {
      requestPromises.synchronized({
        requestPromises.remove(requestId) match {
          case Some(p) => p.failure(new TimeoutException("Response timeout"))
          case _ => {}
        }
      })

      responseTimeoutTasks.synchronized({
        responseTimeoutTasks.remove(requestId)
      })
    })

    responseTimeoutTasks.synchronized({
      responseTimeoutTasks(requestId) = timeoutFuture
    })

    sendMessage(OpCode.Request, Some(requestId), Some(message))

    promise.future
  }

  def reconnect(newSocket: ConvergenceServerSocket): Unit = {
    socket = newSocket
    heartbeatHelper.start()
  }

  private[this] def sendMessage(opCode: String, requestMessageId: Option[Long], message: Option[ProtocolMessage]): Unit = {
    val envelope = MessageEnvelope(opCode, requestMessageId, message)
    socket.send(envelope.toJson())
  }

  private[this] def onSocketMessage(json: String): Unit = {
    heartbeatHelper.messageReceived()
    // FIXME handle error
    val envelope = MessageEnvelope(json).get
    
    envelope.opCode match {
      case OpCode.Normal => onNormalMessage(envelope.extractBody())
      case OpCode.Ping => onPing()
      case OpCode.Pong => {}
      case OpCode.Request => onRequest(envelope)
      case OpCode.Reply => onReply(envelope)
    }
  }

  private[this] def onSocketClosed(): Unit = {
    heartbeatHelper.stop()
    connectionHandler lift ConnectionClosed()
  }

  private[this] def onSocketDropped(): Unit = {
    heartbeatHelper.stop()
    connectionHandler lift ConnectionDropped()
  }

  private[this] def onSocketError(message: String): Unit = {
    connectionHandler lift ConnectionError(message)
  }

  private[this] def onNormalMessage(message: ProtocolMessage): Unit = {
    if (!message.isInstanceOf[IncomingProtocolNormalMessage]) {
      // throw something
    }
    
    connectionHandler lift MessageReceived(message.asInstanceOf[IncomingProtocolNormalMessage])
  }

  private[this] def onPing(): Unit = {
    sendMessage(OpCode.Pong, None, None)
  }

  private[this] def onRequest(envelope: MessageEnvelope): Unit = {
    // Verify body. and req id.
    val protocolMessage = envelope.extractBody()
    
    if (!protocolMessage.isInstanceOf[IncomingProtocolRequestMessage]) {
      // FIXME throw some exception becasue this must be a request message.
    }
    
    val p = Promise[OutgoingProtocolResponseMessage]

    p.future.onComplete({
      case Success(message) => sendMessage(OpCode.Reply, Some(envelope.reqId.get), Some(message))
      case Failure(error) => // FIXME reply with error
    })(ec)

    connectionHandler lift RequestReceived(protocolMessage.asInstanceOf[IncomingProtocolRequestMessage], p)
  }

  private[this] def onReply(envelope: MessageEnvelope): Unit = {
    // TODO need to validate that this is here.
    val requestId = envelope.reqId.get
    val message = envelope.body

    responseTimeoutTasks.synchronized({
      responseTimeoutTasks.remove(requestId) match {
        case Some(t) => t.cancel()
        case _ => {}
      }
    })

    requestPromises.synchronized({
      val p = requestPromises.remove(requestId) match {
        case Some(p) => p.success(envelope.extractBody())
        case _ => {}
      }
    })
  }

  private[this] def handleHeartbeat: PartialFunction[HeartbeatEvent, Unit] = {
    case PingRequest =>
    case PongTimeout =>
  }

  private[this] def invalidMessage(): Unit = {

  }
}