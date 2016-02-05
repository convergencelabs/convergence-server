package com.convergencelabs.server.testkit

import scala.concurrent.duration._
import scala.language.postfixOps
import org.json4s._
import org.json4s.JsonAST.JObject
import org.json4s.jackson.JsonMethods._
import org.scalatest.BeforeAndAfterAll
import org.scalatest.FunSuite
import com.convergencelabs.server.frontend.realtime.AuthenticationResponseMessage
import com.convergencelabs.server.frontend.realtime.CloseRealtimeModelRequestMessage
import com.convergencelabs.server.frontend.realtime.HandshakeRequestMessage
import com.convergencelabs.server.frontend.realtime.HandshakeResponseMessage
import com.convergencelabs.server.frontend.realtime.HandshakeResponseMessage
import com.convergencelabs.server.frontend.realtime.MessageEnvelope
import com.convergencelabs.server.frontend.realtime.ModelDataRequestMessage
import com.convergencelabs.server.frontend.realtime.ModelDataResponseMessage
import com.convergencelabs.server.frontend.realtime.ModelFqnData
import com.convergencelabs.server.frontend.realtime.OpenRealtimeModelRequestMessage
import com.convergencelabs.server.frontend.realtime.OpenRealtimeModelResponseMessage
import com.convergencelabs.server.frontend.realtime.OperationAcknowledgementMessage
import com.convergencelabs.server.frontend.realtime.OperationSubmissionMessage
import com.convergencelabs.server.frontend.realtime.StringInsertOperationData
import com.convergencelabs.server.frontend.realtime.SuccessMessage
import com.convergencelabs.server.frontend.realtime.AuthenticationRequestMessage

class MockClientTest extends FunSuite with BeforeAndAfterAll {
  
  val testServer =  new TestServer(
      "test-server/mono-server-application.conf",
      Map(
        "convergence" -> "test-server/convergence.json.gz",
        "namespace1-domain1" -> "test-server/domain.json.gz"))
  
  override def beforeAll(): Unit = {
    testServer.start()
  }
  
  override def afterAll(): Unit = {
    testServer.stop()
  }
  
  test("Basic model test") {
    val client = new MockConvergenceClient("ws://localhost:8080/domain/namespace1/domain1")
    client.connect()

    client.sendRequest(HandshakeRequestMessage(false, None, None))
    val handhsakeResponse = client.expectMessageClass(5 seconds, classOf[HandshakeResponseMessage])

    client.sendRequest(AuthenticationRequestMessage("password", None, Some("testuser"), Some("testpass")))
    val authResponse = client.expectMessageClass(5 seconds, classOf[AuthenticationResponseMessage])

    client.sendRequest(OpenRealtimeModelRequestMessage(ModelFqnData("collection", "model"), true))

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