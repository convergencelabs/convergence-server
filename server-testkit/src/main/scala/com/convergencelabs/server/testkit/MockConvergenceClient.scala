package com.convergencelabs.server.testkit

import java.net.URI
import java.util.concurrent.LinkedBlockingDeque

import scala.concurrent.duration.Duration
import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.FiniteDuration
import scala.util.Failure
import scala.util.Success

import org.java_websocket.client.WebSocketClient
import org.java_websocket.drafts.Draft_17
import org.java_websocket.handshake.ServerHandshake

import grizzled.slf4j.Logging
import io.convergence.proto.message.ConvergenceMessage
import scalapb.GeneratedMessage
import io.convergence.proto.Outgoing
import io.convergence.proto.Request
import io.convergence.proto.Response

class MockConvergenceClient(serverUri: String)
    extends WebSocketClient(new URI(serverUri), new Draft_17())
    with Logging {

  private val queue = new LinkedBlockingDeque[ConvergenceMessage]()

  override def connect(): Unit = {
    logger.info("Connecting...")
    super.connect()
  }
  
  override def onOpen(handshakedata: ServerHandshake): Unit = {
    logger.info("Connection opened")
  }

  override def onClose(code: Int, reason: String, remote: Boolean): Unit = {
    logger.info("closed with exit code " + code + " additional info: " + reason);
  }

  def sendNormal(message: GeneratedMessage with Outgoing): ConvergenceMessage = {
    val envelope = ConvergenceMessage(message, None, None)
    sendMessage(envelope)
    envelope
  }

  var reqId = 0L

  def sendRequest(message: GeneratedMessage with Request): ConvergenceMessage = {
    val envelope = ConvergenceMessage(message, Some(reqId), None)
    sendMessage(envelope)
    reqId = reqId + 1
    envelope
  }

  def sendResponse(reqId: Long, message: GeneratedMessage with Response): ConvergenceMessage = {
    val envelope = ConvergenceMessage(message, None, Some(reqId))
    sendMessage(envelope)
    envelope
  }

  def sendMessage(message: ConvergenceMessage): Unit = {
    val json = MessageSerializer.writeJson(message)
    send(json)
    logger.warn("SEND: " + json)
  }

  override def onMessage(message: String): Unit = {
    logger.warn("RCV : " + message)
    ConvergenceMessage(message) match {
      case Success(envelope) => {
        envelope.b match {
          case p: PingMessage => onPing()
          case p: PongMessage => {}
          case _ => queue.add(envelope)
        }
      }
      case Failure(e) => throw e
    }
  }

  def onPing(): Unit = {
    sendMessage(ConvergenceMessage(PongMessage(), None, None))
  }

  override def onError(ex: Exception): Unit = {
    logger.info("an error occurred:" + ex);
  }

  def expectMessage(max: FiniteDuration): ConvergenceMessage = receiveOne(max)
  
  def expectMessageClass[C <: ProtocolMessage](max: FiniteDuration, c: Class[C]): (C, ConvergenceMessage) =
    expectMessageClass_internal(max, c)

  private def expectMessageClass_internal[C <: ProtocolMessage](max: FiniteDuration, c: Class[C]): (C, ConvergenceMessage) = {
    val envelope = receiveOne(max)
    assert(envelope ne null, s"timeout ($max) during expectMsgClass waiting for $c")

    val message = envelope.b
    assert(c isInstance message, s"expected $c, found ${message.getClass}")

    (message.asInstanceOf[C], envelope)
  }

  def receiveOne(max: Duration): ConvergenceMessage = {
    val envelope =
      if (max == 0.seconds) {
        queue.pollFirst
      } else if (max.isFinite) {
        queue.pollFirst(max.length, max.unit)
      } else {
        queue.takeFirst
      }

    assert(envelope ne null, s"timeout ($max) during receive one")
    envelope
  }
}