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
import com.convergencelabs.server.frontend.realtime.proto.OutgoingProtocolNormalMessage
import com.convergencelabs.server.frontend.realtime.proto.OutgoingProtocolRequestMessage
import grizzled.slf4j.Logging
import com.convergencelabs.server.frontend.realtime.proto.ErrorData
import com.convergencelabs.server.util.concurrent.ErrorException
import com.convergencelabs.server.frontend.realtime.proto.ErrorMessage
import com.convergencelabs.server.frontend.realtime.proto.IncomingProtocolResponseMessage
import com.convergencelabs.server.util.concurrent.ErrorException
import com.convergencelabs.server.frontend.realtime.proto.MessageSerializer

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
case class RequestReceived(message: IncomingProtocolRequestMessage, replyCallback: ReplyCallback) extends ProtocolMessageEvent

case class ConnectionClosed() extends ConnectionEvent
case class ConnectionDropped() extends ConnectionEvent
case class ConnectionError(message: String) extends ConnectionEvent

class ProtocolConnection(
  private[this] var socket: ConvergenceServerSocket,
  private[this] val protocolConfig: ProtocolConfiguration,
  private[this] val heartbeatEnabled: scala.Boolean,
  private[this] val scheduler: Scheduler,
  private[this] val ec: ExecutionContext)
    extends Logging {

  implicit val formats = Serialization.formats(NoTypeHints)

  val heartbeatHelper = new HearbeatHelper(
    5,
    10,
    scheduler,
    ec,
    handleHeartbeat)

  if (heartbeatEnabled) {
    heartbeatHelper.start()
  }

  socket.handler = {
    case SocketMessage(message) => onSocketMessage(message)
    case SocketClosed() => onSocketClosed()
    case SocketDropped() => onSocketDropped()
    case SocketError(message) => onSocketError(message)
  }

  import com.convergencelabs.server.frontend.realtime.ProtocolConnection.State._

  var nextRequestId = 0L
  val requests = mutable.Map[Long, RequestRecord]()
  var state = Connected

  private[realtime] var eventHandler: PartialFunction[ConnectionEvent, Unit] = {
    case _ => {}
  }

  def send(message: OutgoingProtocolNormalMessage): Unit = {
    sendMessage(OpCode.Normal, None, Some(message))
  }

  def request(message: OutgoingProtocolRequestMessage)(implicit executor: ExecutionContext): Future[IncomingProtocolResponseMessage] = {
    val requestId = nextRequestId
    nextRequestId += 1

    val replyPromise = Promise[IncomingProtocolResponseMessage]

    val timeout = Duration.create(50, TimeUnit.MILLISECONDS)
    val timeoutFuture = scheduler.scheduleOnce(timeout)(() => {
      requests.synchronized({
        requests.remove(requestId) match {
          case Some(record) => {
            record.promise.failure(new TimeoutException("Response timeout"))
          }
          case _ => {}
        }
      })
    })

    val sent = sendMessage(OpCode.Request, Some(requestId), Some(message))
    requests(requestId) = RequestRecord(requestId, replyPromise, timeoutFuture, sent.`type`.get)
    replyPromise.future
  }

  def abort(reason: String): Unit = {
    logger.debug(s"Aborting connection: $reason")
    heartbeatHelper.stop()
    socket.abort(reason)
  }

  def close(): Unit = {
    logger.debug(s"Closing connection")
    if (heartbeatHelper.started) {
      heartbeatHelper.stop()
    }
    socket.close("closed normally")
  }

  def sendMessage(opCode: String, requestMessageId: Option[Long], message: Option[ProtocolMessage]): MessageEnvelope = {
    val envelope = MessageEnvelope(opCode, requestMessageId, message)
    socket.send(envelope.toJson())
    if (envelope.opCode != OpCode.Ping && envelope.opCode != OpCode.Pong) {
      logger.debug(envelope.toJson())
    }
    envelope
  }

  private[this] def onSocketMessage(json: String): Unit = {
    heartbeatHelper.messageReceived()
    // FIXME handle error
    val envelope = MessageEnvelope(json).get

    if (envelope.opCode != OpCode.Ping && envelope.opCode != OpCode.Pong) {
      logger.debug(envelope.toJson())
    }

    envelope.opCode match {
      case OpCode.Normal => onNormalMessage(envelope)
      case OpCode.Ping => onPing()
      case OpCode.Pong => {}
      case OpCode.Request => onRequest(envelope)
      case OpCode.Reply => onReply(envelope)
    }
  }

  private[this] def onSocketClosed(): Unit = {
    logger.debug("Socket closed")
    heartbeatHelper.stop()
    eventHandler lift ConnectionClosed()
  }

  private[this] def onSocketDropped(): Unit = {
    logger.debug("Socket dropped")
    heartbeatHelper.stop()
    eventHandler lift ConnectionDropped()
  }

  private[this] def onSocketError(message: String): Unit = {
    logger.debug("Socket error")
    eventHandler lift ConnectionError(message)
  }

  private[this] def onNormalMessage(envelope: MessageEnvelope): Unit = {
    val message = MessageSerializer.extractBody(envelope)
    if (!message.isInstanceOf[IncomingProtocolNormalMessage]) {
      // throw something
    }

    eventHandler lift MessageReceived(message.asInstanceOf[IncomingProtocolNormalMessage])
  }

  private[this] def onPing(): Unit = {
    sendMessage(OpCode.Pong, None, None)
  }

  private[this] def onRequest(envelope: MessageEnvelope): Unit = {
    // Verify body. and req id.
    val protocolMessage = MessageSerializer.extractBody(envelope)

    if (!protocolMessage.isInstanceOf[IncomingProtocolRequestMessage]) {
      // FIXME throw some exception because this must be a request message.
    }

    val p = Promise[OutgoingProtocolResponseMessage]

    eventHandler lift RequestReceived(
      protocolMessage.asInstanceOf[IncomingProtocolRequestMessage],
      new ReplyCallbackImpl(envelope.reqId.get))
  }

  private[this] def onReply(envelope: MessageEnvelope): Unit = {
    // TODO need to validate that this is here.
    val requestId = envelope.reqId.get
    val message = envelope.body

    requests.synchronized({
      requests.remove(requestId) match {
        case Some(record) => {
          record.future.cancel()
          envelope.`type` match {
            case Some("error") => {
              // FIXME
              //              var errorMessage = envelope.extractResponseBody[ErrorMessage]
              record.promise.failure(new ErrorException())
            }
            case None => {
              // FIXME
              val response = MessageSerializer.extractBody(envelope.body.get, record.requestType)
              record.promise.success(response.asInstanceOf[IncomingProtocolResponseMessage])
            }
            case Some(x) => ???
          }

        }
        case _ => {}
      }
    })
  }

  private[this] def handleHeartbeat: PartialFunction[HeartbeatEvent, Unit] = {
    case PingRequest => this.sendMessage(OpCode.Ping, None, None)
    case PongTimeout => // NoOp
  }

  private[this] def invalidMessage(): Unit = {

  }

  class ReplyCallbackImpl(reqId: Long) extends ReplyCallback {
    val p = Promise[OutgoingProtocolResponseMessage]
    def reply(message: OutgoingProtocolResponseMessage): Unit = {
      sendMessage(OpCode.Reply, Some(reqId), Some(message))
      p.success(message)
    }

    def error(cause: Throwable): Unit = {
      cause match {
        case ErrorException(code, details) => {
          sendMessage(OpCode.Reply, Some(reqId), Some(ErrorMessage(code, details)))
        }
        case x => {
          x.printStackTrace()
          sendMessage(OpCode.Reply, Some(reqId), Some(ErrorMessage("unknown", "foo")))
        }
      }
      p.failure(cause)
    }

    def result(): Future[OutgoingProtocolResponseMessage] = {
      p.future
    }
  }
}

trait ReplyCallback {
  def reply(message: OutgoingProtocolResponseMessage): Unit
  def error(cause: Throwable): Unit
  def result(): Future[OutgoingProtocolResponseMessage]
}

case class RequestRecord(id: Long, promise: Promise[IncomingProtocolResponseMessage], future: Cancellable, requestType: String)