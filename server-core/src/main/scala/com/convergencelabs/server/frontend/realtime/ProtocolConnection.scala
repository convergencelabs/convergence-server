package com.convergencelabs.server.frontend.realtime

import java.util.concurrent.TimeoutException

import scala.collection.mutable
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.Promise
import scala.concurrent.duration.FiniteDuration
import scala.util.Failure
import scala.util.Success

import org.json4s.NoTypeHints
import org.json4s.jackson.Serialization

import com.convergencelabs.server.ProtocolConfiguration
import com.convergencelabs.server.util.concurrent.UnexpectedErrorException

import akka.actor.Cancellable
import akka.actor.Scheduler
import grizzled.slf4j.Logging

sealed trait ConnectionEvent

sealed trait ProtocolMessageEvent extends ConnectionEvent {
  def message: IncomingProtocolMessage
}

case class MessageReceived(message: IncomingProtocolNormalMessage) extends ProtocolMessageEvent
case class RequestReceived(message: IncomingProtocolRequestMessage, replyCallback: ReplyCallback) extends ProtocolMessageEvent

case object ConnectionClosed extends ConnectionEvent
case object ConnectionDropped extends ConnectionEvent
case class ConnectionError(message: String) extends ConnectionEvent

class ProtocolConnection(
  private[this] var socket: ConvergenceServerSocket,
  private[this] val protocolConfig: ProtocolConfiguration,
  private[this] val scheduler: Scheduler,
  private[this] val ec: ExecutionContext)
    extends Logging {

  val heartbeatHelper = new HeartbeatHelper(
    protocolConfig.heartbeatConfig.pingInterval,
    protocolConfig.heartbeatConfig.pongTimeout,
    scheduler,
    ec,
    handleHeartbeat)

  if (protocolConfig.heartbeatConfig.enabled) {
    heartbeatHelper.start()
  }

  socket.handler = {
    case SocketMessage(message) => onSocketMessage(message)
    case SocketClosed() => onSocketClosed()
    case SocketDropped() => onSocketDropped()
    case SocketError(message) => onSocketError(message)
  }

  var nextRequestId = 0L
  val requests = mutable.Map[Long, RequestRecord]()

  private[realtime] var eventHandler: PartialFunction[ConnectionEvent, Unit] = {
    case _ => {}
  }

  def send(message: OutgoingProtocolNormalMessage): Unit = {
    sendMessage(MessageEnvelope(message))
  }

  def request(message: OutgoingProtocolRequestMessage)(implicit executor: ExecutionContext): Future[IncomingProtocolResponseMessage] = {
    val requestId = nextRequestId
    nextRequestId += 1

    val replyPromise = Promise[IncomingProtocolResponseMessage]

    val timeout = protocolConfig.defaultRequestTimeout
    val timeoutFuture = scheduler.scheduleOnce(timeout)(() => {
      requests.synchronized({
        requests.remove(requestId) match {
          case Some(record) => {
            record.promise.failure(new TimeoutException("Response timeout"))
          }
          case _ =>
          // Race condition where the reply just came in under the wire.
          // no action requried.
        }
      })
    })

    val sent = MessageEnvelope(requestId, message)
    sendMessage(sent)

    requests(requestId) = RequestRecord(requestId, replyPromise, timeoutFuture, sent.`type`.get)
    replyPromise.future
  }

  def abort(reason: String): Unit = {
    logger.debug(s"Aborting connection: $reason")
    if (heartbeatHelper.started) {
      heartbeatHelper.stop()
    }
    socket.abort(reason)
  }

  def close(): Unit = {
    logger.debug(s"Closing connection")
    if (heartbeatHelper.started) {
      heartbeatHelper.stop()
    }
    socket.close("closed normally")
  }

  def sendMessage(envelope: MessageEnvelope): Unit = {
    val json = MessageSerializer.writeJson(envelope)
    socket.send(json)
    if (envelope.opCode != OpCode.Ping && envelope.opCode != OpCode.Pong) {
      logger.debug(json)
    }
  }

  private[this] def onSocketMessage(json: String): Unit = {
    if (protocolConfig.heartbeatConfig.enabled) {
      heartbeatHelper.messageReceived()
    }

    MessageEnvelope(json) match {
      case Success(envelope) =>
        MessageSerializer.validateMessageEnvelope(envelope) match {
          case true => handleValidMessage(envelope)
          case false => handleInvalidMessage(s"The message envelope was not valid: $envelope")
        }
      case Failure(cause) =>
        handleInvalidMessage(s"Could not parse JSON message $json")
    }
  }

  private def handleValidMessage(envelope: MessageEnvelope): Unit = {
    if (envelope.opCode != OpCode.Ping && envelope.opCode != OpCode.Pong) {
      logger.debug(MessageSerializer.writeJson(envelope))
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
    if (heartbeatHelper.started) {
      heartbeatHelper.stop()
    }
    eventHandler lift ConnectionClosed
  }

  private[this] def onSocketDropped(): Unit = {
    logger.debug("Socket dropped")
    if (heartbeatHelper.started) {
      heartbeatHelper.stop()
    }
    eventHandler lift ConnectionDropped
  }

  private[this] def onSocketError(message: String): Unit = {
    logger.debug("Socket error")
    eventHandler lift ConnectionError(message)
  }

  private[this] def onNormalMessage(envelope: MessageEnvelope): Unit = {
    val message = MessageSerializer.extractBody(envelope)
    if (!message.isInstanceOf[IncomingProtocolNormalMessage]) {
      handleInvalidMessage("a normal message was received, but the deserialized message was not a normal message")
    } else {
      eventHandler lift MessageReceived(message.asInstanceOf[IncomingProtocolNormalMessage])
    }
  }

  private[this] def onRequest(envelope: MessageEnvelope): Unit = {
    val protocolMessage = MessageSerializer.extractBody(envelope)
    if (!protocolMessage.isInstanceOf[IncomingProtocolRequestMessage]) {
      handleInvalidMessage("a request message was received, but the deserialized message was not a request message")
    } else {
      val p = Promise[OutgoingProtocolResponseMessage]
      eventHandler lift RequestReceived(
        protocolMessage.asInstanceOf[IncomingProtocolRequestMessage],
        new ReplyCallbackImpl(envelope.reqId.get))
    }
  }

  private[this] def onReply(envelope: MessageEnvelope): Unit = {

    val requestId = envelope.reqId.get
    val message = envelope.body
    requests.synchronized({
      requests.remove(requestId) match {
        case Some(record) => {
          record.future.cancel()
          envelope.`type` match {
            case Some(MessageType.Error) =>
              // If there is a type, it should only be "error"
              val errorMessage = MessageSerializer.extractBody(envelope.body.get, classOf[ErrorMessage])
              record.promise.failure(new UnexpectedErrorException(errorMessage.code, errorMessage.details))
            case _ =>
              // There should be no type on a reply message if it is a successful
              // response.
              val response = MessageSerializer.extractBody(envelope.body.get, record.requestType)
              record.promise.success(response.asInstanceOf[IncomingProtocolResponseMessage])
          }
        }
        case None =>
        // This can happen when a reply came for a timed out response.
        // TODO should we log this?
      }
    })
  }

  private[this] def onPing(): Unit = {
    sendMessage(MessageEnvelope(OpCode.Pong, None, None, None))
  }

  private[this] def handleHeartbeat: PartialFunction[HeartbeatEvent, Unit] = {
    case PingRequest =>
      sendMessage(MessageEnvelope(OpCode.Ping, None, None, None))
    case PongTimeout =>
      abort("pong timeout")
      eventHandler lift ConnectionDropped
  }

  private[this] def handleInvalidMessage(error: String): Unit = {
    logger.error(error)
    this.abort(error)
    eventHandler lift ConnectionError(error)
  }

  class ReplyCallbackImpl(reqId: Long) extends ReplyCallback {
    val p = Promise[OutgoingProtocolResponseMessage]
    def reply(message: OutgoingProtocolResponseMessage): Unit = {
      sendMessage(MessageEnvelope(reqId, message))
      p.success(message)
    }

    def error(cause: Throwable): Unit = {
      val errorMessage = cause match {
        case UnexpectedErrorException(code, details) => {
          ErrorMessage(code, details)
        }
        case cause: Throwable => {
          ErrorMessage("unknown", cause.getMessage)
        }
      }

      val serializedErrorMessage = MessageSerializer.decomposeBody(Some(errorMessage))

      val envelope = MessageEnvelope(
        OpCode.Reply,
        Some(reqId),
        Some(MessageType.Error),
        serializedErrorMessage)
        
      sendMessage(envelope)
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
