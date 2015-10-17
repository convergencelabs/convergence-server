package com.convergencelabs.server.test

import scala.concurrent.duration._
import scala.language.postfixOps

import com.convergencelabs.server.frontend.realtime.proto.HandshakeRequestMessage
import com.convergencelabs.server.frontend.realtime.proto.HandshakeResponseMessage
import com.convergencelabs.server.frontend.realtime.proto.HandshakeResponseMessage


object MockClientTest {
  def main(args: Array[String]): Unit = {
    var client = new MockConvergenceClient("ws://localhost:8080/domain/test/test1")
    client.connect()

    client.sendRequest(HandshakeRequestMessage(false, None, None))

    val response = client.expectMessageClass(20 seconds, classOf[HandshakeResponseMessage])
    println(response)
  }
}