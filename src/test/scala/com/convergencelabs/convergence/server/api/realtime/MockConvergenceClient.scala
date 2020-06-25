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

import java.net.URI
import java.nio.ByteBuffer
import java.util.concurrent.LinkedBlockingDeque

import com.convergencelabs.convergence.proto._
import com.convergencelabs.convergence.proto.core._
import grizzled.slf4j.Logging
import org.java_websocket.client.WebSocketClient
import org.java_websocket.drafts.Draft_17
import org.java_websocket.handshake.ServerHandshake
import scalapb.GeneratedMessage

import scala.concurrent.duration.{Duration, DurationInt, FiniteDuration}

private[realtime] class MockConvergenceClient(serverUri: String)
  extends WebSocketClient(new URI(serverUri), new Draft_17())
  with Logging {

  import MockConvergenceClient._

  private val queue = new LinkedBlockingDeque[ConvergenceMessage]()
  var nextReqId = 0

  override def connect(): Unit = {
    logger.info("Connecting...")
    super.connect()
  }

  override def onOpen(handshakeData: ServerHandshake): Unit = {
    info("Connection opened")
  }

  override def onClose(code: Int, reason: String, remote: Boolean): Unit = {
    info(s"Connection closed {code: $code, reason: '$reason'}")
  }

  override def onMessage(message: String): Unit = {
    throw new UnsupportedOperationException("The convergence protocol does not support text messages")
  }

  override def onMessage(bytes: ByteBuffer): Unit = {
    val received = ConvergenceMessage.parseFrom(bytes.array())
    debug("RCV : " + received)

    if (received.body.isPing) {
      onPing()
    } else if (received.body.isPong) {
      // no-op
    } else {
      this.queue.add(received)
    }
  }

  override def onError(ex: Exception): Unit = {
    error("an error occurred", ex)
  }

  def sendNormal(message: GeneratedMessage with NormalMessage): ConvergenceMessage = {
    val convergenceMessage = ConvergenceMessage()
      .withBody(ConvergenceMessageBodyUtils.toBody(message))
    sendMessage(convergenceMessage)
    convergenceMessage
  }

  def sendRequest(message: ClientRequestMessage): ConvergenceMessage = {
    val convergenceMessage = ConvergenceMessage()
      .withBody(ConvergenceMessageBodyUtils.toBody(message))
      .withRequestId(nextReqId)
    sendMessage(convergenceMessage)
    nextReqId = nextReqId + 1
    convergenceMessage
  }

  def sendResponse(reqId: Int, message: ClientResponseMessage): ConvergenceMessage = {
    val convergenceMessage = ConvergenceMessage()
      .withBody(ConvergenceMessageBodyUtils.toBody(message))
      .withResponseId(reqId)
    sendMessage(convergenceMessage)
    convergenceMessage
  }

  private[this] def sendMessage(message: ConvergenceMessage): Unit = {
    val bytes = message.toByteArray
    send(bytes)
    debug("SND: " + message)
  }

  def expectMessage(max: FiniteDuration): ConvergenceMessage = receiveMessage(max)

  def expectMessageClass[C <: GeneratedMessage with ServerMessage](max: FiniteDuration, c: Class[C]): (C, ConvergenceMessage) = {
    val convergenceMessage = receiveMessage(max)
    assert(convergenceMessage != null, s"timeout ($max) during expectMsgClass waiting for $c")

    val body = ConvergenceMessageBodyUtils.fromBody(convergenceMessage.body)

    assert(c isInstance body, s"expected $c, found ${body.getClass}")

    (body.asInstanceOf[C], convergenceMessage)
  }

  private[this] def receiveMessage(max: Duration): ConvergenceMessage = {
    val envelope =
      if (max == 0.seconds) {
        queue.pollFirst
      } else if (max.isFinite) {
        queue.pollFirst(max.length, max.unit)
      } else {
        queue.takeFirst
      }

    assert(envelope ne null, s"timeout ($max) receiving message")
    envelope
  }

  private[this] def onPing(): Unit = {
    sendMessage(ConvergenceMessage().withPong(PongMessage()))
  }
}

private[realtime] object MockConvergenceClient {
  type ClientNormalMessage = GeneratedMessage with NormalMessage with ClientMessage
  type ClientRequestMessage = GeneratedMessage with RequestMessage with ClientMessage
  type ClientResponseMessage = GeneratedMessage with ResponseMessage with ClientMessage

  type ServerNormalMessage = GeneratedMessage with NormalMessage with ServerMessage
  type ServerRequestMessage = GeneratedMessage with RequestMessage with ServerMessage
  type ServerResponseMessage = GeneratedMessage with ResponseMessage with ServerMessage
}