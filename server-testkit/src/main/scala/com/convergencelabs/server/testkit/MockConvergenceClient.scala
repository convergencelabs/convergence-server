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
import com.convergencelabs.server.frontend.realtime.IncomingProtocolNormalMessage
import com.convergencelabs.server.frontend.realtime.IncomingProtocolRequestMessage
import com.convergencelabs.server.frontend.realtime.IncomingProtocolResponseMessage
import com.convergencelabs.server.frontend.realtime.MessageEnvelope
import com.convergencelabs.server.frontend.realtime.MessageSerializer
import com.convergencelabs.server.frontend.realtime.OpCode
import com.convergencelabs.server.frontend.realtime.ProtocolMessage
import grizzled.slf4j.Logging
import com.convergencelabs.server.frontend.realtime.PingMessage
import com.convergencelabs.server.frontend.realtime.PongMessage

class MockConvergenceClient(serverUri: String)
    extends WebSocketClient(new URI(serverUri), new Draft_17())
    with Logging {

  private val queue = new LinkedBlockingDeque[MessageEnvelope]()

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

  def sendNormal(message: IncomingProtocolNormalMessage): MessageEnvelope = {
    val envelope = MessageEnvelope(message, None, None)
    sendMessage(envelope)
    envelope
  }

  var reqId = 0L

  def sendRequest(message: IncomingProtocolRequestMessage): MessageEnvelope = {
    val envelope = MessageEnvelope(message, Some(reqId), None)
    sendMessage(envelope)
    reqId = reqId + 1
    envelope
  }

  def sendResponse(reqId: Long, message: IncomingProtocolResponseMessage): MessageEnvelope = {
    val envelope = MessageEnvelope(message, None, Some(reqId))
    sendMessage(envelope)
    envelope
  }

  def sendMessage(message: MessageEnvelope): Unit = {
    val json = MessageSerializer.writeJson(message)
    send(json)
    logger.warn("SEND: " + json)
  }

  override def onMessage(message: String): Unit = {
    logger.warn("RCV : " + message)
    MessageEnvelope(message) match {
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
    sendMessage(MessageEnvelope(PongMessage(), None, None))
  }

  override def onError(ex: Exception): Unit = {
    logger.info("an error occurred:" + ex);
  }

  def expectMessage(max: FiniteDuration): MessageEnvelope = receiveOne(max)
  
  def expectMessageClass[C <: ProtocolMessage](max: FiniteDuration, c: Class[C]): (C, MessageEnvelope) =
    expectMessageClass_internal(max, c)

  private def expectMessageClass_internal[C <: ProtocolMessage](max: FiniteDuration, c: Class[C]): (C, MessageEnvelope) = {
    val envelope = receiveOne(max)
    assert(envelope ne null, s"timeout ($max) during expectMsgClass waiting for $c")

    val message = envelope.b
    assert(c isInstance message, s"expected $c, found ${message.getClass}")

    (message.asInstanceOf[C], envelope)
  }

  def receiveOne(max: Duration): MessageEnvelope = {
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