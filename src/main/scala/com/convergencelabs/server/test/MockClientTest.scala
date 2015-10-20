package com.convergencelabs.server.test

import scala.concurrent.duration._
import scala.language.postfixOps
import com.convergencelabs.server.frontend.realtime.proto.HandshakeRequestMessage
import com.convergencelabs.server.frontend.realtime.proto.HandshakeResponseMessage
import com.convergencelabs.server.frontend.realtime.proto.HandshakeResponseMessage
import com.convergencelabs.server.frontend.realtime.proto.OpenRealtimeModelRequestMessage
import com.convergencelabs.server.frontend.realtime.proto.ModelFqnData
import com.convergencelabs.server.frontend.realtime.proto.PasswordAuthenticationRequestMessage
import com.convergencelabs.server.frontend.realtime.proto.AuthenticationResponseMessage

object MockClientTest {
  def main(args: Array[String]): Unit = {
    var client = new MockConvergenceClient("ws://localhost:8080/domain/test/test1")
    client.connect()

    client.sendRequest(HandshakeRequestMessage(false, None, None))
    val handhsakeResponse = client.expectMessageClass(5 seconds, classOf[HandshakeResponseMessage])
    
    client.sendRequest(PasswordAuthenticationRequestMessage("test", "test"))
    val authResponse = client.expectMessageClass(5 seconds, classOf[AuthenticationResponseMessage])
    
    client.sendRequest(OpenRealtimeModelRequestMessage(ModelFqnData("collection", "model")))
    
    val openResponse = client.expectMessage(5 seconds)
    println(openResponse)

    client.close()
  }
}