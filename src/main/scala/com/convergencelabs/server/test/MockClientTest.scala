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
import com.convergencelabs.server.frontend.realtime.proto.ErrorMessage
import com.convergencelabs.server.frontend.realtime.proto.ModelDataRequestMessage
import com.convergencelabs.server.frontend.realtime.proto.MessageEnvelope
import com.convergencelabs.server.frontend.realtime.proto.ModelDataResponseMessage
import org.json4s.JsonAST.JObject
import com.convergencelabs.server.frontend.realtime.proto.OpenRealtimeModelResponseMessage

import org.json4s._
import org.json4s.jackson.JsonMethods._

object MockClientTest {
  def main(args: Array[String]): Unit = {
    var client = new MockConvergenceClient("ws://localhost:8080/domain/test/test1")
    client.connect()

    client.sendRequest(HandshakeRequestMessage(false, None, None))
    val handhsakeResponse = client.expectMessageClass(5 seconds, classOf[HandshakeResponseMessage])
    
    client.sendRequest(PasswordAuthenticationRequestMessage("test", "test"))
    val authResponse = client.expectMessageClass(5 seconds, classOf[AuthenticationResponseMessage])
    
    client.sendRequest(OpenRealtimeModelRequestMessage(ModelFqnData("collection", "model")))
    
    // FIXME we have a problem with the resource id stuff.
    val (dataRequest, MessageEnvelope(_, Some(reqId), _, _)) = client.expectMessageClass(5 seconds, classOf[ModelDataRequestMessage])
    println(dataRequest)
    
    client.sendResponse(reqId, ModelDataResponseMessage(JObject("key" -> JString("value"))))
    
    val openResponse = client.expectMessageClass(5 seconds, classOf[OpenRealtimeModelResponseMessage])

    client.close()
  }
}