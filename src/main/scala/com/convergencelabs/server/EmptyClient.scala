package com.convergencelabs.server

import java.net.URI
import org.java_websocket.client.WebSocketClient
import org.java_websocket.drafts.Draft
import org.java_websocket.handshake.ServerHandshake
import org.java_websocket.drafts.Draft_76

class EmptyClient(serverUri: URI, draft: Draft) extends WebSocketClient(serverUri, draft) {

  def this(serverUri: URI) = this(serverUri, new Draft_76())

  override def onOpen(handshakedata: ServerHandshake): Unit = {
    System.out.println("new connection opened");
  }

  override def onClose(code: Int, reason: String, remote: Boolean): Unit = {
    System.out.println("closed with exit code " + code + " additional info: " + reason);
  }

  override def onMessage(message: String): Unit = {
    System.out.println("received message: " + message);
  }

  override def onError(ex: Exception): Unit = {
    System.err.println("an error occurred:" + ex);
  }
}

sealed trait SimulationEvent
case class OutgoingMessage() extends SimulationEvent
case class ExpectedMessage() extends SimulationEvent