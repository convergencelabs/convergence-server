package com.convergencelabs.server.api.realtime

import akka.actor.ActorSystem
import akka.testkit.{TestKit, TestProbe}
import com.convergencelabs.convergence.proto._
import com.convergencelabs.convergence.proto.core._
import com.convergencelabs.convergence.proto.model._
import com.convergencelabs.server.{HeartbeatConfiguration, ProtocolConfiguration}
import com.google.protobuf.timestamp.Timestamp
import org.json4s.NoTypeHints
import org.json4s.jackson.Serialization
import org.scalatest.OptionValues.convertOptionToValuable
import org.scalatest.TryValues.convertTryToSuccessOrFailure
import org.scalatest.{Assertions, BeforeAndAfterAll, Matchers, WordSpecLike}
import org.scalatest.mockito.MockitoSugar

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps

// scalastyle:off magic.number
class ProtocolConnectionSpec
  extends TestKit(ActorSystem("ProtocolConnectionSpec"))
  with WordSpecLike
  with BeforeAndAfterAll
  with Matchers
  with MockitoSugar
  with Assertions {

  implicit val formats = Serialization.formats(NoTypeHints)
  implicit val ec = system.dispatcher

  val session = "session"
  val code = "code"
  val errorMessage = "errorMessage"
  val collectionId = "c"
  val modelId = "m"
  val autoCreateId = 1

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  "A ProtocolConnection" when {

    "sending a normal message" must {
      "send the correct message envelope" in new TestFixture(system) {
        val toSend = OperationAcknowledgementMessage("id1", 4, 5, Some(Timestamp(10)))
        connection.send(toSend)

        val SendUnprocessedMessage(convergenceMessage) = this.clientActor.expectMsgClass(10 millis, classOf[SendUnprocessedMessage])
        connection.serializeAndSend(convergenceMessage)

        val OutgoingBinaryMessage(message) = this.connectionActor.expectMsgClass(10 millis, classOf[OutgoingBinaryMessage])
        val sentMessage = ConvergenceMessage.parseFrom(message)

        sentMessage.body.operationAck shouldBe defined
        sentMessage.getOperationAck shouldBe toSend
      }
    }

    "sending a request message" must {
      "send the correct message envelope" in new TestFixture(system) {
        val toSend = AutoCreateModelConfigRequestMessage(autoCreateId)
        connection.request(toSend)

        val SendUnprocessedMessage(convergenceMessage) = this.clientActor.expectMsgClass(10 millis, classOf[SendUnprocessedMessage])
        connection.serializeAndSend(convergenceMessage)

        val OutgoingBinaryMessage(sentBytes) = this.connectionActor.expectMsgClass(10 millis, classOf[OutgoingBinaryMessage])
        val sentMessage = ConvergenceMessage.parseFrom(sentBytes)

        sentMessage.requestId shouldBe defined
        sentMessage.getModelAutoCreateConfigRequest shouldBe toSend
      }
    }

    "receiving a request" must {
      "emit a request received event" in new TestFixture(system) {
        val handshake = HandshakeRequestMessage(false, None)
        val message = ConvergenceMessage()
          .withRequestId(1)
          .withHandshakeRequest(handshake)
        val bytes = message.toByteArray

        val RequestReceived(request, r) = connection.onIncomingMessage(bytes).get.value.asInstanceOf[RequestReceived]
        request shouldBe handshake
      }
    }

    "receiving a normal message" must {
      "emit a error event and abort the connection if an invalid byte array is recieved" in new TestFixture(system) {
        connection.onIncomingMessage(Array()).failure
      }

      "emit a message received event" in new TestFixture(system) {
        val opSubmission = OperationSubmissionMessage()
          .withResourceId("r")
          .withSequenceNumber(1)
          .withContextVersion(2)
          .withOperation(OperationData().withCompoundOperation(CompoundOperationData(List())))
        val message = ConvergenceMessage()
          .withOperationSubmission(opSubmission)

        val bytes = message.toByteArray
        val MessageReceived(received) = connection.onIncomingMessage(bytes).get.value.asInstanceOf[MessageReceived]
        received shouldBe opSubmission
      }

      "emit a error event and abort the connection if a normal message has a request Id" in new TestFixture(system) {
        val opSubmission = OperationSubmissionMessage()
          .withResourceId("r")
          .withSequenceNumber(1)
          .withContextVersion(2)
          .withOperation(OperationData().withCompoundOperation(CompoundOperationData(List())))

        val message = ConvergenceMessage()
          .withRequestId(1)
          .withOperationSubmission(opSubmission)
        val bytes = message.toByteArray

        connection.onIncomingMessage(bytes).failure
      }
    }

    "receiving a ping message" must {
      "respond with a pong" in new TestFixture(system) {
        val message = ConvergenceMessage()
          .withPing(PingMessage())
        val bytes = message.toByteArray
        connection.onIncomingMessage(bytes).success

        val OutgoingBinaryMessage(sentBytes) = this.connectionActor.expectMsgClass(10 millis, classOf[OutgoingBinaryMessage])
        val received = ConvergenceMessage.parseFrom(sentBytes)

        val expected = ConvergenceMessage().withPong(PongMessage())
        received shouldBe expected
      }
    }

    "responding to a request" must {
      "send a correct reply envelope for a success" in new TestFixture(system) {
        val handshake = HandshakeRequestMessage(false, None)
        val message = ConvergenceMessage()
          .withRequestId(1)
          .withHandshakeRequest(handshake)

        val bytes = message.toByteArray
        val RequestReceived(m, cb) = connection.onIncomingMessage(bytes).success.value.value.asInstanceOf[RequestReceived]

        val response = HandshakeResponseMessage().withSuccess(true)
        cb.reply(response)

        val expectedResponseEnvelope = ConvergenceMessage()
          .withResponseId(1)
          .withHandshakeResponse(response)

        val SendUnprocessedMessage(convergenceMessage) = this.clientActor.expectMsgClass(10 millis, classOf[SendUnprocessedMessage])
        connection.serializeAndSend(convergenceMessage)

        val OutgoingBinaryMessage(sentBytes) = this.connectionActor.expectMsgClass(10 millis, classOf[OutgoingBinaryMessage])
        val sentMessage = ConvergenceMessage.parseFrom(sentBytes)
        sentMessage shouldBe expectedResponseEnvelope
      }

      "send a correct reply envelope for an unexpected error" in new TestFixture(system) {
        val message = HandshakeRequestMessage(false, None)
        val envelope = ConvergenceMessage()
          .withRequestId(1)
          .withHandshakeRequest(message)

        val bytes = envelope.toByteArray
        val RequestReceived(m, cb) = connection.onIncomingMessage(bytes).success.value.value.asInstanceOf[RequestReceived]

        cb.unexpectedError(errorMessage)

        val SendUnprocessedMessage(convergenceMessage) = this.clientActor.expectMsgClass(10 millis, classOf[SendUnprocessedMessage])
        connection.serializeAndSend(convergenceMessage)

        val OutgoingBinaryMessage(sentBytes) = this.connectionActor.expectMsgClass(10 millis, classOf[OutgoingBinaryMessage])
        val sentMessage = ConvergenceMessage.parseFrom(sentBytes)

        sentMessage.body.error shouldBe defined
        sentMessage.getError.code shouldBe "unknown"
        sentMessage.getError.message shouldBe errorMessage
      }

      "send a correct reply envelope for an expected error" in new TestFixture(system) {
        val handshakeRequest = HandshakeRequestMessage(false, None)
        val sentMessage = ConvergenceMessage()
          .withRequestId(1)
          .withHandshakeRequest(handshakeRequest)

        val bytes = sentMessage.toByteArray
        val RequestReceived(m, cb) = connection.onIncomingMessage(bytes).success.value.value.asInstanceOf[RequestReceived]

        cb.expectedError(code, errorMessage)

        val SendUnprocessedMessage(convergenceMessage) = this.clientActor.expectMsgClass(10 millis, classOf[SendUnprocessedMessage])
        connection.serializeAndSend(convergenceMessage)

        val OutgoingBinaryMessage(sentBytes) = this.connectionActor.expectMsgClass(10 millis, classOf[OutgoingBinaryMessage])
        val sentError = ConvergenceMessage.parseFrom(sentBytes)

        sentError.body.error shouldBe defined
        sentError.getError.code shouldBe code
        sentError.getError.message shouldBe errorMessage
      }
    }

    "set to ping" must {
      "ping within the specified interval" in {
        val clientActor = new TestProbe(system)
        val connectionActor = new TestProbe(system)

        val protoConfig = ProtocolConfiguration(
          100 millis,
          100 millis,
          HeartbeatConfiguration(
            true,
            10 millis,
            10 seconds))

        val pingConnecction = new ProtocolConnection(
          clientActor.ref,
          connectionActor.ref,
          protoConfig,
          system.scheduler,
          system.dispatcher)

        val OutgoingBinaryMessage(replyMessage) = connectionActor.expectMsgClass(100 millis, classOf[OutgoingBinaryMessage])
        val convergenceMessage = ConvergenceMessage.parseFrom(replyMessage)

        val exepcted = ConvergenceMessage().withPing(PingMessage())
        convergenceMessage shouldBe exepcted
      }

      "timeout within the specified interval" in {
        val clientActor = new TestProbe(system)
        val connectionActor = new TestProbe(system)

        val protoConfig = ProtocolConfiguration(
          100 millis,
          100 millis,
          HeartbeatConfiguration(
            true,
            10 millis,
            10 millis))

        val pingConnecction = new ProtocolConnection(
          clientActor.ref,
          connectionActor.ref,
          protoConfig,
          system.scheduler,
          system.dispatcher)

        clientActor.expectMsg(100 millis, PongTimeout)
      }
    }

    "receiving a reply" must {

      "ignore when the reply has no request" in new TestFixture(system) {
        val autoCreate = AutoCreateModelConfigResponseMessage()
          .withCollection(collectionId)
          .withData(ObjectValue("vid1", Map()))

        val sentMessage = ConvergenceMessage()
          .withResponseId(1)
          .withModelAutoCreateConfigResponse(autoCreate)

        val bytes = sentMessage.toByteArray
        connection.onIncomingMessage(bytes).success
      }

      "resolve the request future with the proper message" in new TestFixture(system) {
        val toSend = AutoCreateModelConfigRequestMessage(autoCreateId)
        val f = connection.request(toSend)

        val SendUnprocessedMessage(convergenceMessage) = this.clientActor.expectMsgClass(10 millis, classOf[SendUnprocessedMessage])
        connection.serializeAndSend(convergenceMessage)

        val OutgoingBinaryMessage(sentBytes) = this.connectionActor.expectMsgClass(10 millis, classOf[OutgoingBinaryMessage])
        val sentMessage = ConvergenceMessage.parseFrom(sentBytes)

        val replyMessage = AutoCreateModelConfigResponseMessage()
          .withCollection(collectionId)
          .withData(ObjectValue("vid2", Map()))
        val replyEnvelope = ConvergenceMessage()
          .withResponseId(sentMessage.getRequestId)
          .withModelAutoCreateConfigResponse(replyMessage)

        connection.onIncomingMessage(replyEnvelope.toByteArray).success.value shouldBe None

        val response = Await.result(f, 10 millis)

        response shouldBe replyMessage
      }

      "resolve the future with a failure if an error is recieved" in new TestFixture(system) {
        val toSend = AutoCreateModelConfigRequestMessage(autoCreateId)
        val f = connection.request(toSend)

        val SendUnprocessedMessage(convergenceMessage) = this.clientActor.expectMsgClass(10 millis, classOf[SendUnprocessedMessage])
        connection.serializeAndSend(convergenceMessage)

        val OutgoingBinaryMessage(sentBytes) = this.connectionActor.expectMsgClass(10 millis, classOf[OutgoingBinaryMessage])
        val sentMessage = ConvergenceMessage.parseFrom(sentBytes)

        val replyMessage = ErrorMessage(code, errorMessage, Map("foo" -> "bar"))
        val replyEnvelope = ConvergenceMessage()
          .withResponseId(sentMessage.getRequestId)
          .withError(replyMessage)

        connection.onIncomingMessage(replyEnvelope.toByteArray).success.value shouldBe None

        Await.ready(f, 10 millis)
        val cause = f.value.get.failure.exception
        cause shouldBe a[ClientErrorResponseException]

        val errorException = cause.asInstanceOf[ClientErrorResponseException]

        errorException.message shouldBe errorMessage
      }
    }
  }

  class TestFixture(system: ActorSystem) {
    val clientActor = new TestProbe(system)
    val connectionActor = new TestProbe(system)

    val protoConfig = ProtocolConfiguration(
      100 millis,
      100 millis,
      HeartbeatConfiguration(
        false,
        5 seconds,
        10 seconds))

    val connection = new ProtocolConnection(
      clientActor.ref,
      connectionActor.ref,
      protoConfig,
      system.scheduler,
      system.dispatcher)
  }
}
