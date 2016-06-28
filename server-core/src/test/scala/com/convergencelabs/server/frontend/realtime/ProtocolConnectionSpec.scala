package com.convergencelabs.server.frontend.realtime

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps

import org.json4s.NoTypeHints
import org.json4s.jackson.Serialization
import org.scalatest.Assertions
import org.scalatest.BeforeAndAfterAll
import org.scalatest.Finders
import org.scalatest.Matchers
import org.scalatest.OptionValues.convertOptionToValuable
import org.scalatest.TryValues.convertTryToSuccessOrFailure
import org.scalatest.WordSpecLike
import org.scalatest.mock.MockitoSugar

import com.convergencelabs.server.HeartbeatConfiguration
import com.convergencelabs.server.ProtocolConfiguration
import com.convergencelabs.server.domain.model.data.ObjectValue

import akka.actor.ActorSystem
import akka.testkit.TestKit
import akka.testkit.TestProbe

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
  val details = "details"
  val collectionId = "c"
  val modelId = "m"

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  "A ProtocolConnection" when {

    "sending a normal message" must {
      "send the correct message envelope" in new TestFixture(system) {
        val toSend = OperationAcknowledgementMessage("id1", 4, 5, 10)
        connection.send(toSend)

        val OutgoingTextMessage(message) = this.connectionActor.expectMsgClass(10 millis, classOf[OutgoingTextMessage])
        val sentEnvelope = MessageSerializer.readJson[MessageEnvelope](message)

        sentEnvelope.q shouldBe None
        sentEnvelope.b shouldBe toSend
      }
    }

    "sending a request message" must {
      "send the correct message envelope" in new TestFixture(system) {
        val toSend = ModelDataRequestMessage(collectionId, modelId)
        connection.request(toSend)

        val OutgoingTextMessage(message) = this.connectionActor.expectMsgClass(10 millis, classOf[OutgoingTextMessage])
        val sentEnvelope = MessageSerializer.readJson[MessageEnvelope](message)

        sentEnvelope.q shouldBe defined
        sentEnvelope.b shouldBe toSend
      }
    }

    "receiving a request" must {
      "emit a request received event" in new TestFixture(system) {
        val message = HandshakeRequestMessage(false, None)
        val envelope = MessageEnvelope(message, Some(1L), None)
        val json = MessageSerializer.writeJson(envelope)

        val RequestReceived(x, r) = connection.onIncomingMessage(json).success.value.value.asInstanceOf[RequestReceived]
        x shouldBe message
      }
    }

    "receiving a normal message" must {
      "emit a error event and abort the connection if an invalid json is recieved" in new TestFixture(system) {
        connection.onIncomingMessage("{").failure
      }

      "emit a message received event" in new TestFixture(system) {
        val message = OperationSubmissionMessage(session, 1L, 2L, CompoundOperationData(List()))
        val envelope = MessageEnvelope(
          message,
          None,
          None)
        val json = MessageSerializer.writeJson(envelope)
        val MessageReceived(x) = connection.onIncomingMessage(json).success.value.value.asInstanceOf[MessageReceived]
        x shouldBe message
      }

      "emit a error event and abort the connection if a message with a request Id" in new TestFixture(system) {
        val message = OperationSubmissionMessage(session, 1L, 2L, CompoundOperationData(List()))
        val envelope = MessageEnvelope(
          message,
          Some(1L),
          None)
        val json = MessageSerializer.writeJson(envelope)

        connection.onIncomingMessage(json).failure
      }
    }

    "receiving a ping message" must {
      "respond with a pong" in new TestFixture(system) {
        val envelope = MessageEnvelope(
          PingMessage(),
          None,
          None)
        val json = MessageSerializer.writeJson(envelope)
        connection.onIncomingMessage(json).success

        val OutgoingTextMessage(message) = this.connectionActor.expectMsgClass(10 millis, classOf[OutgoingTextMessage])
        val sentEnvelope = MessageSerializer.readJson[MessageEnvelope](message)

        sentEnvelope shouldBe MessageEnvelope(PongMessage(), None, None)
      }
    }

    "responding to a request" must {
      "send a correct reply envelope for a success" in new TestFixture(system) {
        val message = HandshakeRequestMessage(false, None)
        val envelope = MessageEnvelope(message, Some(1L), None)

        val json = MessageSerializer.writeJson(envelope)
        val RequestReceived(m, cb) = connection.onIncomingMessage(json).success.value.value.asInstanceOf[RequestReceived]

        val response = HandshakeResponseMessage(true, None, None, None)
        cb.reply(response)

        val expectedResponseEnvelope = MessageEnvelope(response, None, Some(1L))

        val OutgoingTextMessage(replyMessage) = this.connectionActor.expectMsgClass(10 millis, classOf[OutgoingTextMessage])
        val sentEnvelope = MessageSerializer.readJson[MessageEnvelope](replyMessage)
        sentEnvelope shouldBe expectedResponseEnvelope
      }

      "send a correct reply envelope for an unexpected error" in new TestFixture(system) {
        val message = HandshakeRequestMessage(false, None)
        val envelope = MessageEnvelope(message, Some(1L), None)

        val json = MessageSerializer.writeJson(envelope)
        val RequestReceived(m, cb) = connection.onIncomingMessage(json).success.value.value.asInstanceOf[RequestReceived]

        cb.unexpectedError(details)

        val OutgoingTextMessage(replyMessage) = this.connectionActor.expectMsgClass(10 millis, classOf[OutgoingTextMessage])
        val sentEnvelope = MessageSerializer.readJson[MessageEnvelope](replyMessage)
        val errorMessage = sentEnvelope.b.asInstanceOf[ErrorMessage]

        errorMessage.c shouldBe "unknown"
        errorMessage.d shouldBe details
      }

      "send a correct reply envelope for an expected error" in new TestFixture(system) {
        val message = HandshakeRequestMessage(false, None)
        val envelope = MessageEnvelope(message, Some(1L), None)

        val json = MessageSerializer.writeJson(envelope)
        val RequestReceived(m, cb) = connection.onIncomingMessage(json).success.value.value.asInstanceOf[RequestReceived]

        cb.expectedError(code, details)

        val OutgoingTextMessage(replyMessage) = this.connectionActor.expectMsgClass(10 millis, classOf[OutgoingTextMessage])
        val sentEnvelope = MessageSerializer.readJson[MessageEnvelope](replyMessage)

        val errorMessage = sentEnvelope.b.asInstanceOf[ErrorMessage]
        errorMessage.c shouldBe code
        errorMessage.d shouldBe details
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

        val OutgoingTextMessage(replyMessage) = connectionActor.expectMsgClass(100 millis, classOf[OutgoingTextMessage])
        val sentEnvelope = MessageSerializer.readJson[MessageEnvelope](replyMessage)

        sentEnvelope shouldBe MessageEnvelope(PingMessage(), None, None)
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
        val message = ModelDataResponseMessage(ObjectValue("vid1", Map()))
        val envelope = MessageEnvelope(message, None, Some(1L))

        val json = MessageSerializer.writeJson(envelope)
        connection.onIncomingMessage(json).success
      }

      "resolve the request future with the proper message" in new TestFixture(system) {
        val toSend = ModelDataRequestMessage(collectionId, modelId)
        val f = connection.request(toSend)

        val OutgoingTextMessage(message) = this.connectionActor.expectMsgClass(10 millis, classOf[OutgoingTextMessage])
        val sentEnvelope = MessageSerializer.readJson[MessageEnvelope](message)

        val replyMessage = ModelDataResponseMessage(ObjectValue("vid2", Map()))
        val replyEnvelope = MessageEnvelope(replyMessage, None, sentEnvelope.q)

        val replyJson = MessageSerializer.writeJson(replyEnvelope)
        connection.onIncomingMessage(replyJson).success.value shouldBe None

        val response = Await.result(f, 10 millis)

        response shouldBe replyMessage
      }

      "resolve the future with a failure if an error is recieved" in new TestFixture(system) {
        val toSend = ModelDataRequestMessage(collectionId, modelId)
        val f = connection.request(toSend)

        val OutgoingTextMessage(message) = this.connectionActor.expectMsgClass(10 millis, classOf[OutgoingTextMessage])
        val sentEnvelope = MessageSerializer.readJson[MessageEnvelope](message)

        val replyMessage = ErrorMessage(code, details)
        val replyEnvelope = MessageEnvelope(replyMessage, None, sentEnvelope.q)

        val replyJson = MessageSerializer.writeJson(replyEnvelope)
        connection.onIncomingMessage(replyJson).success.value shouldBe None

        Await.ready(f, 10 millis)
        val cause = f.value.get.failure.exception
        cause shouldBe a[ClientErrorResponseException]

        val errorException = cause.asInstanceOf[ClientErrorResponseException]

        errorException.message shouldBe details
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
