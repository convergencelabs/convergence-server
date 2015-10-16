package com.convergencelabs.server

import java.net.URI
import org.java_websocket.drafts.Draft_10
import com.convergencelabs.server.frontend.realtime.proto.OutgoingProtocolMessage
import com.convergencelabs.server.frontend.realtime.proto.MessageEnvelope
import scala.concurrent.duration.FiniteDuration
import com.convergencelabs.server.frontend.realtime.proto.OpCode
import com.convergencelabs.server.frontend.realtime.proto.HandshakeRequestMessage
import scala.concurrent.duration._
import scala.language.postfixOps
import com.convergencelabs.server.domain.HandshakeResponse
import com.convergencelabs.server.frontend.realtime.proto.HandshakeResponseMessage
import grizzled.slf4j.Logging

object MockClientTest extends Logging {
  def main(args: Array[String]): Unit = {
    var client = new MockConvergenceClient("ws://localhost:8080/domain/test/test1")
    client.connect()
    
    client.sendRequest(HandshakeRequestMessage(false, None, None))
    
    val response = client.expectMessageClass(20 seconds, classOf[HandshakeResponseMessage])
    println(response)
  }
}