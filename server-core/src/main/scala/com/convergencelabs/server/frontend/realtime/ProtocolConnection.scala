package com.convergencelabs.server.frontend.realtime

import java.util.concurrent.TimeoutException

import scala.collection.mutable
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.Promise
import scala.util.Failure
import scala.util.Success

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

  private[this] val heartbeatHelper = new HeartbeatHelper(
    protocolConfig.heartbeatConfig.pingInterval,
    protocolConfig.heartbeatConfig.pongTimeout,
    scheduler,
    ec,
    handleHeartbeat)

  if (protocolConfig.heartbeatConfig.enabled) {
    heartbeatHelper.start()
  }

  private[this] val socketHandler: PartialFunction[ConvergenceServerSocketEvent, Unit] = {
    case SocketMessage(message) => onSocketMessage(message)
    case SocketClosed() => onSocketClosed()
    case SocketDropped() => onSocketDropped()
    case SocketError(message) => onSocketError(message)
  }

  this.socket.setHandler(socketHandler)

  var nextRequestId = 0L
  val requests = mutable.Map[Long, RequestRecord]()

  private[realtime] var eventHandler: PartialFunction[ConnectionEvent, Unit] = {
    case _ => { ??? }
  }

  def ready(): Unit = {
    this.socket.ready()
  }

  def send(message: OutgoingProtocolNormalMessage): Unit = {
    sendMessage(MessageEnvelope(message, None, None))
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

    val sent = MessageEnvelope(message, Some(requestId), None)
    sendMessage(sent)

    requests(requestId) = RequestRecord(requestId, replyPromise, timeoutFuture)
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
    if (!envelope.b.isInstanceOf[PingMessage] && !envelope.b.isInstanceOf[PongMessage]) {
      logger.debug(json)
    }
  }

  private[this] def onSocketMessage(json: String): Unit = {
    if (protocolConfig.heartbeatConfig.enabled) {
      heartbeatHelper.messageReceived()
    }

    MessageEnvelope(json) match {
      case Success(envelope) =>
        handleValidMessage(envelope)
      case Failure(cause) =>
        logger.error(cause)
        handleInvalidMessage(s"Could not parse JSON message $json")
    }
  }

  private def handleValidMessage(envelope: MessageEnvelope): Unit = {
    if (!envelope.b.isInstanceOf[PingMessage] && !envelope.b.isInstanceOf[PongMessage]) {
      logger.debug(MessageSerializer.writeJson(envelope))
    }

    envelope match {
      case MessageEnvelope(PingMessage(), None, None) =>
        onPing()
      case MessageEnvelope(PongMessage(), None, None) =>
      // No Op
      case MessageEnvelope(ErrorMessage(_, _), None, Some(_)) =>
        onReply(envelope)
      case MessageEnvelope(ErrorMessage(_, _), None, None) =>
        onNormalMessage(envelope)
      case MessageEnvelope(msg, None, None) if msg.isInstanceOf[IncomingProtocolNormalMessage] =>
        onNormalMessage(envelope)
      case MessageEnvelope(msg, Some(_), None) if msg.isInstanceOf[IncomingProtocolRequestMessage] =>
        onRequest(envelope)
      case MessageEnvelope(msg, None, Some(_)) if msg.isInstanceOf[IncomingProtocolResponseMessage] =>
        onReply(envelope)
      case _ =>
        handleInvalidMessage("Invalid messame.")
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
    eventHandler lift MessageReceived(envelope.b.asInstanceOf[IncomingProtocolNormalMessage])
  }

  private[this] def onRequest(envelope: MessageEnvelope): Unit = {
    val p = Promise[OutgoingProtocolResponseMessage]
    eventHandler lift RequestReceived(
      envelope.b.asInstanceOf[IncomingProtocolRequestMessage],
      new ReplyCallbackImpl(envelope.q.get))

  }

  private[this] def onReply(envelope: MessageEnvelope): Unit = {
    val requestId = envelope.p.get
    val message = envelope.b
    requests.synchronized({
      requests.remove(requestId) match {
        case Some(record) => {
          record.future.cancel()
          envelope.b match {
            case ErrorMessage(code, details) =>
              record.promise.failure(new ClientErrorResponseException(code, details))
            case _ =>
              // There should be no type on a reply message if it is a successful
              // response.
              record.promise.success(message.asInstanceOf[IncomingProtocolResponseMessage])
          }
        }
        case None =>
        // This can happen when a reply came for a timed out response.
        // TODO should we log this?
      }
    })
  }

  private[this] def onPing(): Unit = {
    sendMessage(MessageEnvelope(PongMessage(), None, None))
  }

  private[this] def handleHeartbeat: PartialFunction[HeartbeatEvent, Unit] = {
    case PingRequest =>
      sendMessage(MessageEnvelope(PingMessage(), None, None))
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
    def reply(message: OutgoingProtocolResponseMessage): Unit = {
      sendMessage(MessageEnvelope(message, None, Some(reqId)))
    }

    def unknownError(): Unit = {
      unexpectedError("An unkown error has occured")
    }

    def unexpectedError(details: String): Unit = {
      expectedError("unknown", details)
    }

    def expectedError(code: String, details: String): Unit = {
      val errorMessage = ErrorMessage(code, details)

      val envelope = MessageEnvelope(
        errorMessage,
        None,
        Some(reqId))

      sendMessage(envelope)
    }
  }
}

trait ReplyCallback {
  def reply(message: OutgoingProtocolResponseMessage): Unit
  def unknownError(): Unit
  def unexpectedError(details: String): Unit
  def expectedError(code: String, details: String): Unit
}

case class RequestRecord(id: Long, promise: Promise[IncomingProtocolResponseMessage], future: Cancellable)
