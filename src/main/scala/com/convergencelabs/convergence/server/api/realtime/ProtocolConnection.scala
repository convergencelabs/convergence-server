/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is part of the Convergence Server, which is released under
 * the terms of the GNU General Public License version 3 (GPLv3). A copy
 * of the GPLv3 should have been provided along with this file, typically
 * located in the "LICENSE" file, which is part of this source code package.
 * Alternatively, see <https://www.gnu.org/licenses/gpl-3.0.html> for the
 * full text of the GPLv3 license, if it was not provided.
 */

package com.convergencelabs.convergence.server.api.realtime

import java.util.concurrent.TimeoutException

import akka.actor.Cancellable
import akka.actor.typed.{ActorRef, Scheduler}
import com.convergencelabs.convergence.proto.ConvergenceMessage._
import com.convergencelabs.convergence.proto._
import com.convergencelabs.convergence.proto.core._
import com.convergencelabs.convergence.server.ProtocolConfiguration
import com.convergencelabs.convergence.server.actor.CborSerializable
import com.convergencelabs.convergence.server.api.realtime.ConnectionActor.OutgoingBinaryMessage
import grizzled.slf4j.Logging
import org.json4s.JsonAST.JValue
import scalapb.GeneratedMessage

import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Success, Try}

/**
 * The [[ProtocolConnection]] class manages the Convergence Protocol Buffer,
 * web socket protocol. It's primary functions are to encode and decode
 * incoming and outgoing protocol buffer message; to implement the request /
 * response message correlation, and to provide a keep alive heart beat to
 * keep the web socket open and to detect when a connection has been silently
 * lost.
 *
 * @param clientActor     The client actor to deliver incoming message events to.
 * @param connectionActor The web socket connection actor to send outgoing
 *                        messages to.
 * @param protocolConfig  The protocol configuration object that configures the
 *                        connection behavior.
 * @param scheduler       The scheduler to use to schedule periodic work, such as
 *                        heartbeats.
 * @param ec              The execution context to use for asynchronous work.
 */
class ProtocolConnection(clientActor: ActorRef[ClientActor.FromProtocolConnection],
                         connectionActor: ActorRef[OutgoingBinaryMessage],
                         protocolConfig: ProtocolConfiguration,
                         scheduler: Scheduler,
                         ec: ExecutionContext)
  extends Logging {

  import ProtocolConnection._

  private[this] implicit val executor: ExecutionContext = ec

  private[this] val heartbeatHelper = new HeartbeatHelper(
    protocolConfig.heartbeatConfig.pingInterval,
    protocolConfig.heartbeatConfig.pongTimeout,
    scheduler,
    ec,
    handleHeartbeat)

  if (protocolConfig.heartbeatConfig.enabled) {
    heartbeatHelper.start()
  }

  private[this] var nextRequestId = 0
  private[this] val requests = mutable.Map[Int, RequestRecord]()

  /**
   * Sends a normal message to the client without an expectation of
   * a response.
   *
   * @param message The message to send.
   */
  def send(message: ServerNormalMessage): Unit = {
    val body = ConvergenceMessageBodyUtils.toBody(message)
    val convergenceMessage = ConvergenceMessage().withBody(body)
    sendMessage(convergenceMessage)
  }

  /**
   * Sends a request message to the client with an expectation of
   * a response.
   *
   * @param message The message to send to the client.
   * @return A Future which will be completed with the response
   *         message from the client if successful.
   */
  def request(message: ServerRequestMessage): Future[ClientResponseMessage] = {
    val requestId = nextRequestId
    nextRequestId += 1

    val replyPromise = Promise[ClientResponseMessage]

    val timeout = protocolConfig.defaultRequestTimeout
    val timeoutFuture = scheduler.scheduleOnce(timeout, () => {
      requests.synchronized({
        requests.remove(requestId) match {
          case Some(record) =>
            record.promise.failure(
              new TimeoutException(s"A request timeout occurred waiting for a response to: $message"))
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

    requests.synchronized {
      requests(requestId) = RequestRecord(requestId, replyPromise, timeoutFuture)
    }

    replyPromise.future
  }

  /**
   * Handles an incoming serialized message from the client, decodes its and
   * emits the proper events.
   *
   * @param message The incoming message as a byte array.
   * @return The decoded and validated message.
   */
  def onIncomingMessage(message: Array[Byte]): Try[Option[ProtocolMessageEvent]] = {
    if (protocolConfig.heartbeatConfig.enabled) {
      heartbeatHelper.messageReceived()
    }

    ConvergenceMessage.validate(message) match {
      case Success(envelope) =>
        handleValidMessage(envelope)

      case Failure(cause) =>
        val message = "Could not decode incoming binary protocol message"
        error(message, cause)
        Failure(new IllegalArgumentException(message))
    }
  }

  /**
   * Releases the resources of this ProtocolConnection. The connection
   * will no longer be usable after calling this method.
   */
  def dispose(): Unit = {
    if (heartbeatHelper.started()) {
      heartbeatHelper.stop()
    }
  }

  /**
   * Serializes and sends a message to the connection actor.
   *
   * @param convergenceMessage The message to serialize and send.
   */
  def serializeAndSend(convergenceMessage: ConvergenceMessage): Unit = {
    val bytes = convergenceMessage.toByteArray
    connectionActor ! OutgoingBinaryMessage(bytes)
    if (!convergenceMessage.body.isPing && !convergenceMessage.body.isPong) {
      logger.debug("SND: " + convergenceMessage)
    }
  }

  private[this] def handleValidMessage(convergenceMessage: ConvergenceMessage): Try[Option[ProtocolMessageEvent]] = Try {
    if (!convergenceMessage.body.isPing && !convergenceMessage.body.isPong) {
      logger.debug("RCV: " + convergenceMessage)
    }

    ConvergenceMessageBodyUtils.fromBody(convergenceMessage.body) match {
      case Some(_: PingMessage) =>
        onPing()
        None
      case Some(_: PongMessage) =>
        // No-Op
        None
      case Some(message: ClientRequestMessage) =>
        if (convergenceMessage.requestId.isEmpty) {
          throw new IllegalArgumentException("A request message must have a requestId")
        }

        if (convergenceMessage.responseId.isDefined) {
          throw new IllegalArgumentException("A request message cannot have a responseId")
        }

        Some(RequestReceived(message, new ReplyCallbackImpl(convergenceMessage.requestId.get)))
      case Some(message: ClientResponseMessage) =>
        if (convergenceMessage.requestId.isDefined) {
          throw new IllegalArgumentException("A response message cannot have a requestId")
        }

        if (convergenceMessage.responseId.isEmpty) {
          throw new IllegalArgumentException("A response message must have a responseId")
        }

        onReply(message, convergenceMessage.responseId.get)
        None
      case Some(message: ClientNormalMessage) =>
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

  private[this] def sendMessage(convergenceMessage: ConvergenceMessage): Unit = {
    clientActor ! ClientActor.SendUnprocessedMessage(convergenceMessage)
  }

  private[this] def onReply(message: ClientResponseMessage, responseId: Int): Unit = {
    requests.synchronized({
      requests.remove(responseId) match {
        case Some(record) =>
          record.future.cancel()
          message match {
            case ErrorMessage(code, message, _, _) =>
              record.promise.failure(ClientErrorResponseException(code, message))
            case _ =>
              // There should be no type on a reply message if it is a successful
              // response.
              record.promise.success(message)
          }
        case None =>
        // This can happen when a reply came for a timed out response.
      }
    })
  }

  private[this] def onPing(): Unit = {
    serializeAndSend(ConvergenceMessage().withPong(PongMessage()))
  }

  private[this] def handleHeartbeat: PartialFunction[HeartbeatEvent, Unit] = {
    case PingRequest =>
      serializeAndSend(ConvergenceMessage().withPing(PingMessage()))
    case PongTimeout =>
      clientActor ! ClientActor.PongTimeout
  }

  /**
   * A helper class that implements the ReplyCallback trait that will be
   * delivered to consumers.
   *
   * @param reqId The request id this reply callback will respond to.
   */
  private[this] class ReplyCallbackImpl(reqId: Int) extends ReplyCallback {
    override def reply(message: ServerResponseMessage): Unit = {
      sendMessage(ConvergenceMessage(None, Some(reqId), ConvergenceMessageBodyUtils.toBody(message)))
    }

    override def unknownError(): Unit = {
      unexpectedError("An unknown error has occurred, check the server logs for more details.")
    }

    override def unexpectedError(message: String): Unit = {
      expectedError(ErrorCodes.Unknown, message)
    }

    override def expectedError(code: ErrorCodes.ErrorCode, message: String): Unit = {
      expectedError(code, message, Map[String, JValue]())
    }

    override def expectedError(code: ErrorCodes.ErrorCode, message: String, details: Map[String, JValue]): Unit = {
      val protoDetails = JsonProtoConverter.jValueMapToValueMap(details)
      val errorMessage = ErrorMessage(code.toString, message, protoDetails)

      val envelope = ConvergenceMessage(
        None,
        Some(reqId),
        Body.Error(errorMessage))

      sendMessage(envelope)
    }

    override def timeoutError(): Unit = {
      expectedError(ErrorCodes.Timeout, "An internal server timeout occurred")
    }
  }

}

object ProtocolConnection {
  type ServerNormalMessage = GeneratedMessage with NormalMessage with ServerMessage
  type ServerRequestMessage = GeneratedMessage with RequestMessage with ServerMessage
  type ServerResponseMessage = GeneratedMessage with ResponseMessage with ServerMessage

  type ClientNormalMessage = GeneratedMessage with NormalMessage with ClientMessage
  type ClientRequestMessage = GeneratedMessage with RequestMessage with ClientMessage
  type ClientResponseMessage = GeneratedMessage with ResponseMessage with ClientMessage

  /**
   * The [[ProtocolMessageEvent]] defines the events related to receiving a
   * message from the client.
   */
  sealed trait ProtocolMessageEvent extends CborSerializable {
    def message: GeneratedMessage
  }


  /**
   * The [[ReplyCallback]] trait defines how a consumer of a protocol message
   * can respond to a request message from the client that expects and
   * explicit reply.
   */
  trait ReplyCallback {
    /**
     * Indicates a successful handling of the request message and responds with
     * the supplied message.
     *
     * @param message The message to reply with.
     */
    def reply(message: ServerResponseMessage): Unit

    /**
     * Responds to the request with an unknown error response.
     */
    def unknownError(): Unit

    /**
     * Responds to the request indicated a timeout occurred.
     */
    def timeoutError(): Unit

    /**
     * Responds with an unexpected error but supplies a human readable
     * error message to the client.
     *
     * @param message The human readable message to respond with.
     */
    def unexpectedError(message: String): Unit

    /**
     * Replies with a well known error condition.
     *
     * @param code    The machine readable code indicting the well known error.
     * @param message A human readable message with additional details.
     */
    def expectedError(code: ErrorCodes.ErrorCode, message: String): Unit

    /**
     * Replies with a well known error condition.
     *
     * @param code    The machine readable code indicting the well known error.
     * @param message A human readable message with additional details.
     * @param details Additional machine readable data related to the error.
     */
    def expectedError(code: ErrorCodes.ErrorCode, message: String, details: Map[String, JValue]): Unit
  }

  /**
   * Indicates an incoming message that does not expect a response.
   *
   * @param message Thee incoming normal message.
   */
  final case class MessageReceived(message: ClientNormalMessage) extends ProtocolMessageEvent

  /**
   * Indicates an incoming message that expects a response.
   *
   * @param message       Thee incoming request message.
   * @param replyCallback A call back that will response to the request.
   */
  final case class RequestReceived(message: ClientRequestMessage, replyCallback: ReplyCallback) extends ProtocolMessageEvent


  private final case class RequestRecord(id: Long, promise: Promise[ClientResponseMessage], future: Cancellable)

}
