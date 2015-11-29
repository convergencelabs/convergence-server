package com.convergencelabs.server.testkit

import scala.concurrent.duration._
import scala.language.postfixOps

import org.json4s._
import org.json4s.JsonAST.JObject
import org.json4s.jackson.JsonMethods._
import org.scalatest.BeforeAndAfterAll
import org.scalatest.FunSuite

import com.convergencelabs.server.frontend.realtime.proto.AuthenticationResponseMessage
import com.convergencelabs.server.frontend.realtime.proto.CloseRealtimeModelRequestMessage
import com.convergencelabs.server.frontend.realtime.proto.HandshakeRequestMessage
import com.convergencelabs.server.frontend.realtime.proto.HandshakeResponseMessage
import com.convergencelabs.server.frontend.realtime.proto.HandshakeResponseMessage
import com.convergencelabs.server.frontend.realtime.proto.MessageEnvelope
import com.convergencelabs.server.frontend.realtime.proto.ModelDataRequestMessage
import com.convergencelabs.server.frontend.realtime.proto.ModelDataResponseMessage
import com.convergencelabs.server.frontend.realtime.proto.ModelFqnData
import com.convergencelabs.server.frontend.realtime.proto.OpenRealtimeModelRequestMessage
import com.convergencelabs.server.frontend.realtime.proto.OpenRealtimeModelResponseMessage
import com.convergencelabs.server.frontend.realtime.proto.OperationAcknowledgementMessage
import com.convergencelabs.server.frontend.realtime.proto.OperationSubmissionMessage
import com.convergencelabs.server.frontend.realtime.proto.PasswordAuthenticationRequestMessage
import com.convergencelabs.server.frontend.realtime.proto.StringInsertOperationData
import com.convergencelabs.server.frontend.realtime.proto.SuccessMessage

class MockClientTest extends FunSuite with BeforeAndAfterAll {
  
  val testServer =  new TestServer(
      "test-server/mono-server-application.conf",
      Map(
        "convergence" -> "test-server/convergence.json.gz",
        "namespace1-domain1" -> "test-server/domain.json.gz"))
  
  override def beforeAll(): Unit = {
    testServer.start()
  }
  
  test("An empty Set should have size 0") {
    val client = new MockConvergenceClient("ws://localhost:8080/domain/namespace1/domain1")
    client.connect()

    client.sendRequest(HandshakeRequestMessage(false, None, None))
    val handhsakeResponse = client.expectMessageClass(5 seconds, classOf[HandshakeResponseMessage])

    client.sendRequest(PasswordAuthenticationRequestMessage("test1", "password"))
    val authResponse = client.expectMessageClass(5 seconds, classOf[AuthenticationResponseMessage])

    client.sendRequest(OpenRealtimeModelRequestMessage(ModelFqnData("collection", "model")))

    val (dataRequest, MessageEnvelope(_, Some(reqId), _, _)) = client.expectMessageClass(5 seconds, classOf[ModelDataRequestMessage])
    client.sendResponse(reqId, ModelDataResponseMessage(JObject("key" -> JString("value"))))

    val (openResponse, _) = client.expectMessageClass(5 seconds, classOf[OpenRealtimeModelResponseMessage])

    val opMessage = OperationSubmissionMessage(openResponse.rId, 0L, openResponse.v, StringInsertOperationData(List("key"), false, 0, "x"))
    client.sendNormal(opMessage)

    val opAck = client.expectMessageClass(5 seconds, classOf[OperationAcknowledgementMessage])

    client.sendRequest(CloseRealtimeModelRequestMessage(openResponse.rId))
    val closeResponse = client.expectMessageClass(5 seconds, classOf[SuccessMessage])

    client.close()
  }
}