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
import com.convergencelabs.server.frontend.realtime.SuccessMessage
import com.convergencelabs.server.frontend.realtime.AuthenticationRequestMessage
import com.convergencelabs.server.domain.PasswordAuthRequest
import com.convergencelabs.server.domain.PasswordAuthRequest
import com.convergencelabs.server.frontend.realtime.PasswordAuthRequestMessage
import com.convergencelabs.server.domain.model.data.ObjectValue
import com.convergencelabs.server.domain.model.data.StringValue
import com.convergencelabs.server.frontend.realtime.CloseRealTimeModelSuccessMessage
import com.convergencelabs.server.frontend.realtime.RemoteOperationMessage
import org.scalatest.WordSpecLike
import org.scalatest.Matchers
import org.scalatest.BeforeAndAfter
import com.convergencelabs.server.frontend.realtime.RemoteClientOpenedMessage
import com.convergencelabs.server.frontend.realtime.OperationAcknowledgementMessage

class TwoCientOperationTest extends WordSpecLike with BeforeAndAfterAll  with Matchers {


  val testServer = new TestServer(
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

  "Two connected clients" when {
    "connected to the same model" must {
      "receive operation from each other" in {
        val client1 = newClient("test1")
        val client2 = newClient("test2")
        
        client1.sendRequest(OpenRealtimeModelRequestMessage("people", "person1", true))
        val (openResponse1, _) = client1.expectMessageClass(5 seconds, classOf[OpenRealtimeModelResponseMessage])

        client2.sendRequest(OpenRealtimeModelRequestMessage("people", "person1", true))
        val (openResponse2, _) = client2.expectMessageClass(5 seconds, classOf[OpenRealtimeModelResponseMessage])

        client1.expectMessageClass(5 seconds, classOf[RemoteClientOpenedMessage])
        
        val op1 = StringInsertOperationData("pp1-fname", false, 0, "x")
        val opMessage = OperationSubmissionMessage(openResponse1.r, 0L, openResponse1.v, op1)
        client1.sendNormal(opMessage)
        
        client1.expectMessageClass(5 seconds, classOf[OperationAcknowledgementMessage])

        val (remoteOp1, _) = client2.expectMessageClass(5 seconds, classOf[RemoteOperationMessage])
        remoteOp1.o shouldBe op1
        
        val op2 = StringInsertOperationData("pp1-fname", false, 0, "y")
        val opMessage2 = OperationSubmissionMessage(openResponse1.r, 1L, openResponse1.v, op2)
        client2.sendNormal(opMessage2)
        
        client2.expectMessageClass(5 seconds, classOf[OperationAcknowledgementMessage])
        
        val (remoteOp2, _) = client1.expectMessageClass(5 seconds, classOf[RemoteOperationMessage])
        remoteOp2.o shouldBe op2
        
        client1.close()
        client2.close()
      }
    }
  }

  private[this] def newClient(username: String): MockConvergenceClient = {
    val client = new MockConvergenceClient("ws://localhost:8080/domain/namespace1/domain1")
    client.connectBlocking()

    client.sendRequest(HandshakeRequestMessage(false, None))
    val handhsakeResponse = client.expectMessageClass(5 seconds, classOf[HandshakeResponseMessage])

    client.sendRequest(PasswordAuthRequestMessage(username, "password"))
    val (authResponse, _) = client.expectMessageClass(5 seconds, classOf[AuthenticationResponseMessage])
    assert(authResponse.s, s"Unable to authenticate")
    client
  }
}