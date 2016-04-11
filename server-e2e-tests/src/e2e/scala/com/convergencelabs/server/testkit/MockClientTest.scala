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
import com.convergencelabs.server.frontend.realtime.OpenRealtimeModelRequestMessage
import com.convergencelabs.server.frontend.realtime.OpenRealtimeModelResponseMessage
import com.convergencelabs.server.frontend.realtime.OperationAcknowledgementMessage
import com.convergencelabs.server.frontend.realtime.OperationSubmissionMessage
import com.convergencelabs.server.frontend.realtime.StringInsertOperationData
import com.convergencelabs.server.frontend.realtime.AuthenticationRequestMessage
import com.convergencelabs.server.domain.PasswordAuthRequest
import com.convergencelabs.server.domain.PasswordAuthRequest
import com.convergencelabs.server.frontend.realtime.PasswordAuthRequestMessage
import com.convergencelabs.server.domain.model.data.ObjectValue
import com.convergencelabs.server.domain.model.data.StringValue
import com.convergencelabs.server.frontend.realtime.CloseRealTimeModelSuccessMessage

class MockClientTest extends FunSuite with BeforeAndAfterAll {
  
  val testServer =  new TestServer(
      "test-server/mono-server-application.conf",
      Map(
        "convergence" -> "test-server/convergence.json.gz",
        "namespace1-domain1" -> "test-server/n1-d1.json.gz"))
  
  override def beforeAll(): Unit = {
    testServer.start()
  }
  
  override def afterAll(): Unit = {
    testServer.stop()
  }
  
  test("Basic model test") {
    val client = new MockConvergenceClient("ws://localhost:8080/domain/namespace1/domain1")
    client.connect()

    client.sendRequest(HandshakeRequestMessage(false, None))
    val handhsakeResponse = client.expectMessageClass(5 seconds, classOf[HandshakeResponseMessage])

    client.sendRequest(PasswordAuthRequestMessage("test1", "password"))
    val (authResponse, _) = client.expectMessageClass(5 seconds, classOf[AuthenticationResponseMessage])
    assert(authResponse.s, s"Unable to authenticate")

    client.sendRequest(OpenRealtimeModelRequestMessage("collection", "model", true))

    val (dataRequest, MessageEnvelope(_, Some(reqId), _)) = client.expectMessageClass(5 seconds, classOf[ModelDataRequestMessage])
    client.sendResponse(reqId, ModelDataResponseMessage(ObjectValue("data1", Map("key" -> StringValue("value1", "value")))))

    val (openResponse, _) = client.expectMessageClass(5 seconds, classOf[OpenRealtimeModelResponseMessage])

    val opMessage = OperationSubmissionMessage(openResponse.r, 0L, openResponse.v, StringInsertOperationData("value1", false, 0, "x"))
    client.sendNormal(opMessage)

    val opAck = client.expectMessageClass(5 seconds, classOf[OperationAcknowledgementMessage])

    client.sendRequest(CloseRealtimeModelRequestMessage(openResponse.r))
    val closeResponse = client.expectMessageClass(5 seconds, classOf[CloseRealTimeModelSuccessMessage])

    client.close()
  }
}