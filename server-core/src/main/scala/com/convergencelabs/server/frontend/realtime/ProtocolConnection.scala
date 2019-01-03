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
import scalapb.GeneratedMessage
import io.convergence.proto.Request
import io.convergence.proto.Response
import io.convergence.proto.Normal
import io.convergence.proto.message.ConvergenceMessage.Body
import io.convergence.proto.connection._
import io.convergence.proto.model._
import io.convergence.proto.activity._
import io.convergence.proto.authentication._
import io.convergence.proto.model._
import io.convergence.proto.message._
import io.convergence.proto.common._
import io.convergence.proto.chat._
import io.convergence.proto.permissions._
import io.convergence.proto.presence._
import io.convergence.proto.references._
import io.convergence.proto.identity._
import io.convergence.proto.operations._

sealed trait ProtocolMessageEvent {
  def message: GeneratedMessage
}

case class MessageReceived(message: GeneratedMessage with Normal) extends ProtocolMessageEvent
case class RequestReceived(message: GeneratedMessage with Request, replyCallback: ReplyCallback) extends ProtocolMessageEvent

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

  var nextRequestId = 0
  val requests = mutable.Map[Long, RequestRecord]()

  def send(message: GeneratedMessage with Normal): Unit = {
    val body = ConvergenceMessageBodyUtils.toBody(message)
    val convergenceMessage = ConvergenceMessage().withBody(body)
    sendMessage(convergenceMessage)
  }

  def request(message: GeneratedMessage with Request)(implicit executor: ExecutionContext): Future[Response] = {
    val requestId = nextRequestId
    nextRequestId += 1

    val replyPromise = Promise[GeneratedMessage with Response]

    val timeout = protocolConfig.defaultRequestTimeout
    val timeoutFuture = scheduler.scheduleOnce(timeout)(() => {
      requests.synchronized({
        requests.remove(requestId) match {
          case Some(record) => {
            record.promise.failure(new TimeoutException("Response timeout"))
          }
          case _ =>
          // Race condition where the reply just came in under the wire.
          // no action required.
        }
      })
    })
    
    val body = ConvergenceMessageBodyUtils.toBody(message)
    val convergenceMessage = ConvergenceMessage()
      .withRequestId(requestId)
      .withBody(body)

    sendMessage(convergenceMessage)

    requests(requestId) = RequestRecord(requestId, replyPromise, timeoutFuture)
    replyPromise.future
  }

  def handleClosed(): Unit = {
    logger.debug(s"Protocol connection closed")
    if (heartbeatHelper.started) {
      heartbeatHelper.stop()
    }
  }

  def sendMessage(envelope: io.convergence.proto.message.ConvergenceMessage): Unit = {
    val bytes = envelope.toByteArray
    connectionActor ! OutgoingBinaryMessage(bytes)
    if (!envelope.body.isPing && !envelope.body.isPong) {
      logger.trace("S: " + envelope)
    }
  }

  def onIncomingMessage(message: Array[Byte]): Try[Option[ProtocolMessageEvent]] = {
      if (protocolConfig.heartbeatConfig.enabled) {
      heartbeatHelper.messageReceived()
    }

    ConvergenceMessage.validate(message) match {
      case Success(envelope) =>
        handleValidMessage(envelope)
      case Failure(cause) =>
        val message = "Could not parse incoming protocol message"
        logger.error(message, cause)
        Failure(new IllegalArgumentException(message))
    }
  }

  // scalastyle:off cyclomatic.complexity
  private def handleValidMessage(convergenceMessage: ConvergenceMessage): Try[Option[ProtocolMessageEvent]] = Try {
    if (!convergenceMessage.body.isPing && !convergenceMessage.body.isPong) {
      logger.trace("R: " + convergenceMessage)
    }

    ConvergenceMessageBodyUtils.fromBody(convergenceMessage.body) match {
      case _: PingMessage =>
        onPing()
        None
      case _: PongMessage =>
        // No Opo
        None

      case message: Request =>
        Some(RequestReceived(message, new ReplyCallbackImpl(convergenceMessage.requestId.get)))

      case message: Response =>
        onReply(message, convergenceMessage.responseId.get)
        None

      case message: Normal =>
        if (convergenceMessage.requestId.isDefined) {
          throw new IllegalArgumentException("A normal message cannot have a requestId")
        }
        
        if (convergenceMessage.responseId.isDefined) {
          throw new IllegalArgumentException("A normal message cannot have a responseId")
        }
        
        Some(MessageReceived(message))

      case _ =>
        throw new IllegalArgumentException("Invalid message: " + convergenceMessage)
    }
  }
  // scalastyle:on cyclomatic.complexity

  def dispose(): Unit = {
    if (heartbeatHelper.started) {
      heartbeatHelper.stop()
    }
  }

  

  private[this] def onReply(message: GeneratedMessage with Response, responseId: Int): Unit = {

    requests.synchronized({
      requests.remove(responseId) match {
        case Some(record) => {
          record.future.cancel()
          message match {
            case ErrorMessage(code, message, details) =>
              record.promise.failure(new ClientErrorResponseException(code, message))
            case _ =>
              // There should be no type on a reply message if it is a successful
              // response.
              record.promise.success(message)
          }
        }
        case None =>
        // This can happen when a reply came for a timed out response.
        // TODO should we log this?
      }
    })
  }

  private[this] def onPing(): Unit = {
    sendMessage(io.convergence.proto.message.ConvergenceMessage().withPong(PongMessage()))
  }

  private[this] def handleHeartbeat: PartialFunction[HeartbeatEvent, Unit] = {
    case PingRequest =>
      sendMessage(io.convergence.proto.message.ConvergenceMessage().withPing(PingMessage()))
    case PongTimeout =>
      clientActor ! PongTimeout
  }

  class ReplyCallbackImpl(reqId: Int) extends ReplyCallback {
    def reply(message: GeneratedMessage with Response): Unit = {
      sendMessage(ConvergenceMessage(None, Some(reqId), ConvergenceMessageBodyUtils.toBody(message)))
    }

    def unknownError(): Unit = {
      unexpectedError("An unknown error has occurred")
    }

    def unexpectedError(message: String): Unit = {
      expectedError("unknown", message)
    }

    def expectedError(code: String, message: String): Unit = {
      expectedError(code, message, Map[String, String]())
    }

    def expectedError(code: String, message: String, details: Map[String, String]): Unit = {
      val errorMessage = ErrorMessage(code, message, details)

      val envelope = ConvergenceMessage(
        None,
        Some(reqId),
        Body.Error(errorMessage))

      sendMessage(envelope)
    }
  }
}

trait ReplyCallback {
  def reply(message: GeneratedMessage with Response): Unit
  def unknownError(): Unit
  def unexpectedError(message: String): Unit
  def expectedError(code: String, message: String): Unit
  def expectedError(code: String, message: String, details: Map[String, String]): Unit

}

case class RequestRecord(id: Long, promise: Promise[GeneratedMessage with Response], future: Cancellable)
