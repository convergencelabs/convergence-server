package com.convergencelabs.server.frontend.realtime

import java.util.concurrent.TimeoutException

import scala.collection.mutable
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.Promise
import scala.util.Failure
import scala.util.Success
import scala.util.Try

import com.convergencelabs.server.ProtocolConfiguration

import akka.actor.ActorRef
import akka.actor.Cancellable
import akka.actor.Scheduler
import akka.actor.actorRef2Scala
import grizzled.slf4j.Logging

sealed trait ProtocolMessageEvent {
  def message: IncomingProtocolMessage
}

case class MessageReceived(message: IncomingProtocolNormalMessage) extends ProtocolMessageEvent
case class RequestReceived(message: IncomingProtocolRequestMessage, replyCallback: ReplyCallback) extends ProtocolMessageEvent

class ProtocolConnection(
  private[this] val clientActor: ActorRef,
  private[this] val connectionActor: ActorRef,
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

  var nextRequestId = 0L
  val requests = mutable.Map[Long, RequestRecord]()

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

  def handleClosed(): Unit = {
    logger.debug(s"Protocol connection closed")
    if (heartbeatHelper.started) {
      heartbeatHelper.stop()
    }
  }

  def sendMessage(envelope: MessageEnvelope): Unit = {
    val json = MessageSerializer.writeJson(envelope)
    connectionActor ! OutgoingTextMessage(json)
    if (!envelope.b.isInstanceOf[PingMessage] && !envelope.b.isInstanceOf[PongMessage]) {
      logger.trace("S: " + envelope)
    }
  }

  def onIncomingMessage(json: String): Try[Option[ProtocolMessageEvent]] = {
    if (protocolConfig.heartbeatConfig.enabled) {
      heartbeatHelper.messageReceived()
    }

    MessageEnvelope(json) match {
      case Success(envelope) =>
        handleValidMessage(envelope)
      case Failure(cause) =>
        logger.error(cause)
        Failure(new IllegalArgumentException(s"Could not parse JSON message $json"))
    }
  }

  // scalastyle:off cyclomatic.complexity
  private def handleValidMessage(envelope: MessageEnvelope): Try[Option[ProtocolMessageEvent]] = Try {
    if (!envelope.b.isInstanceOf[PingMessage] && !envelope.b.isInstanceOf[PongMessage]) {
      logger.trace("R: " + envelope)
    }

    envelope match {
      case MessageEnvelope(PingMessage(), None, None) =>
        onPing()
        None
      case MessageEnvelope(PongMessage(), None, None) =>
        // No Opo
        None
      case MessageEnvelope(msg, None, None) if msg.isInstanceOf[IncomingProtocolNormalMessage] =>
        Some(MessageReceived(envelope.b.asInstanceOf[IncomingProtocolNormalMessage]))
      case MessageEnvelope(msg, Some(_), None) if msg.isInstanceOf[IncomingProtocolRequestMessage] =>
        Some(RequestReceived(
          envelope.b.asInstanceOf[IncomingProtocolRequestMessage],
          new ReplyCallbackImpl(envelope.q.get)))
      case MessageEnvelope(msg, None, Some(_)) if msg.isInstanceOf[IncomingProtocolResponseMessage] =>
        onReply(envelope)
        None
      case _ =>
        throw new IllegalArgumentException("Invalid message: " + envelope)
    }
  }
  // scalastyle:on cyclomatic.complexity

  def dispose(): Unit = {
    if (heartbeatHelper.started) {
      heartbeatHelper.stop()
    }
  }

  private[this] def onReply(envelope: MessageEnvelope): Unit = {
    val requestId = envelope.p.get
    val message = envelope.b
    requests.synchronized({
      requests.remove(requestId) match {
        case Some(record) => {
          record.future.cancel()
          envelope.b match {
            case ErrorMessage(code, message, details) =>
              record.promise.failure(new ClientErrorResponseException(code, message))
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
      clientActor ! PongTimeout
  }

  class ReplyCallbackImpl(reqId: Long) extends ReplyCallback {
    def reply(message: OutgoingProtocolResponseMessage): Unit = {
      sendMessage(MessageEnvelope(message, None, Some(reqId)))
    }

    def unknownError(): Unit = {
      unexpectedError("An unkown error has occured")
    }

    def unexpectedError(message: String): Unit = {
      expectedError("unknown", message)
    }
    
    def expectedError(code: String, message: String): Unit = {
      expectedError(code, message, Map())
    }
   
    def expectedError(code: String, message: String, details: Map[String, Any]): Unit = {
      val errorMessage = ErrorMessage(code, message, details)

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
  def unexpectedError(message: String): Unit
  def expectedError(code: String, message: String): Unit
  def expectedError(code: String, message: String, details: Map[String, Any]): Unit

}

case class RequestRecord(id: Long, promise: Promise[IncomingProtocolResponseMessage], future: Cancellable)
