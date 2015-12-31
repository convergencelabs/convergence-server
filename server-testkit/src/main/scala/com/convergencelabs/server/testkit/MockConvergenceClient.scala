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
    val t = MessageSerializer.IncomingMessages.getKey(message.getClass).get
    val msg = MessageSerializer.decomposeBody(Some(message))
    val envelope = MessageEnvelope(OpCode.Normal, None, Some(t), msg)
    sendMessage(envelope)
    envelope
  }

  var reqId = 0L

  def sendRequest(message: IncomingProtocolRequestMessage): MessageEnvelope = {
    val t = MessageSerializer.IncomingMessages.getKey(message.getClass).get
    val msg = MessageSerializer.decomposeBody(Some(message))
    val envelope = MessageEnvelope(OpCode.Request, Some(reqId), Some(t), msg)
    sendMessage(envelope)
    reqId = reqId + 1
    envelope
  }

  def sendResponse(reqId: Long, message: IncomingProtocolResponseMessage): MessageEnvelope = {
    val msg = MessageSerializer.decomposeBody(Some(message))
    val envelope = MessageEnvelope(OpCode.Reply, Some(reqId), None, msg)
    sendMessage(envelope)
    envelope
  }

  def sendMessage(message: MessageEnvelope): Unit = {
    send(message.toJson())
    logger.warn("SEND: " + message.toJson())
  }

  override def onMessage(message: String): Unit = {
    logger.warn("RCV : " + message)
    MessageEnvelope(message) match {
      case Success(envelope) => {
        envelope.opCode match {
          case OpCode.Ping => onPing()
          case OpCode.Pong => {}
          case _ => queue.add(envelope)
        }
      }
      case Failure(e) => throw e
    }
  }

  def onPing(): Unit = {
    sendMessage(MessageEnvelope(OpCode.Pong, None, None, None))
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

    val message = MessageSerializer.extractBody(envelope.body.get, c)
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