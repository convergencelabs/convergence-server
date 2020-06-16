/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is part of the Convergence Server, which is released under
 * the terms of the GNU General Public License version 3 (GPLv3). A copy
 * of the GPLv3 should have been provided along with this file, typically
 * located in the "LICENSE" file, which is part of this source code package.
 * Alternatively, see <https://www.gnu.org/licenses/gpl-3.0.html> for the
 * full text of the GPLv3 license, if it was not provided.
 */

package com.convergencelabs.convergence.server.api.realtime

import akka.actor.testkit.typed.scaladsl
import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import com.convergencelabs.convergence.proto._
import com.convergencelabs.convergence.proto.core._
import com.convergencelabs.convergence.proto.model._
import com.convergencelabs.convergence.server.api.realtime.ClientActor.SendUnprocessedMessage
import com.convergencelabs.convergence.server.api.realtime.ConnectionActor.OutgoingBinaryMessage
import com.convergencelabs.convergence.server.api.realtime.ProtocolConnection.{MessageReceived, RequestReceived}
import com.convergencelabs.convergence.server.{HeartbeatConfiguration, ProtocolConfiguration}
import com.google.protobuf.struct.Value
import com.google.protobuf.timestamp.Timestamp
import org.json4s.jackson.Serialization
import org.json4s.{Formats, NoTypeHints}
import org.scalatest.OptionValues.convertOptionToValuable
import org.scalatest.TryValues.convertTryToSuccessOrFailure
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatest.{Assertions, BeforeAndAfterAll}
import org.scalatestplus.mockito.MockitoSugar

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContextExecutor}
import scala.language.postfixOps

// scalastyle:off magic.number
class ProtocolConnectionSpec
  extends ScalaTestWithActorTestKit
    with AnyWordSpecLike
    with BeforeAndAfterAll
    with Matchers
    with MockitoSugar
    with Assertions {

  implicit val formats: Formats = Serialization.formats(NoTypeHints)
  implicit val ec: ExecutionContextExecutor = testKit.system.executionContext

  private val code = ErrorCodes.Unknown
  private val errorMessage = "errorMessage"
  private val collectionId = "c"
  private val autoCreateId = 1

  override def afterAll(): Unit = testKit.shutdownTestKit()

  "A ProtocolConnection" when {

    "sending a normal message" must {
      "send the correct message envelope" in new TestFixture() {
        {
          val toSend = OperationAcknowledgementMessage("id1", 4, 5, Some(Timestamp(10)))
          connection.send(toSend)

          val SendUnprocessedMessage(convergenceMessage) = this.clientActor.expectMessageType[SendUnprocessedMessage](10 millis)
          connection.serializeAndSend(convergenceMessage)

          val OutgoingBinaryMessage(message) = this.connectionActor.expectMessageType[OutgoingBinaryMessage](10 millis)
          val sentMessage = ConvergenceMessage.parseFrom(message)

          sentMessage.body.operationAck shouldBe defined
          sentMessage.getOperationAck shouldBe toSend
        }
      }
    }

    "sending a request message" must {
      "send the correct message envelope" in new TestFixture() {
        {
          val toSend = AutoCreateModelConfigRequestMessage(autoCreateId)
          connection.request(toSend)

          val SendUnprocessedMessage(convergenceMessage) = this.clientActor.expectMessageType[SendUnprocessedMessage](10 millis)
          connection.serializeAndSend(convergenceMessage)

          val OutgoingBinaryMessage(sentBytes) = this.connectionActor.expectMessageType[OutgoingBinaryMessage](10 millis)
          val sentMessage = ConvergenceMessage.parseFrom(sentBytes)

          sentMessage.requestId shouldBe defined
          sentMessage.getModelAutoCreateConfigRequest shouldBe toSend
        }
      }
    }

    "receiving a request" must {
      "emit a request received event" in new TestFixture() {
        {
          val handshake = HandshakeRequestMessage(reconnect = false, None)
          val message = ConvergenceMessage()
            .withRequestId(1)
            .withHandshakeRequest(handshake)
          val bytes = message.toByteArray

          val RequestReceived(request, _) = connection.onIncomingMessage(bytes).get.value.asInstanceOf[RequestReceived]
          request shouldBe handshake
        }
      }
    }

    "receiving a normal message" must {
      "emit a error event and abort the connection if an invalid byte array is received" in new TestFixture() {
        connection.onIncomingMessage(Array()).failure
      }

      "emit a message received event" in new TestFixture() {
        {
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
      }

      "emit a error event and abort the connection if a normal message has a request Id" in new TestFixture() {
        {
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
    }

    "receiving a ping message" must {
      "respond with a pong" in new TestFixture() {
        {
          val message = ConvergenceMessage()
            .withPing(PingMessage())
          val bytes = message.toByteArray
          connection.onIncomingMessage(bytes).success

          val OutgoingBinaryMessage(sentBytes) = this.connectionActor.expectMessageType[OutgoingBinaryMessage](10 millis)
          val received = ConvergenceMessage.parseFrom(sentBytes)

          val expected = ConvergenceMessage().withPong(PongMessage())
          received shouldBe expected
        }
      }
    }

    "responding to a request" must {
      "send a correct reply envelope for a success" in new TestFixture() {
        {
          val handshake = HandshakeRequestMessage(reconnect = false, None)
          val message = ConvergenceMessage()
            .withRequestId(1)
            .withHandshakeRequest(handshake)

          val bytes = message.toByteArray
          val RequestReceived(_, cb) = connection.onIncomingMessage(bytes).success.value.value.asInstanceOf[RequestReceived]

          val response = HandshakeResponseMessage().withSuccess(true)
          cb.reply(response)

          val expectedResponseEnvelope = ConvergenceMessage()
            .withResponseId(1)
            .withHandshakeResponse(response)

          val SendUnprocessedMessage(convergenceMessage) = this.clientActor.expectMessageType[SendUnprocessedMessage](10 millis)
          connection.serializeAndSend(convergenceMessage)

          val OutgoingBinaryMessage(sentBytes) = this.connectionActor.expectMessageType[OutgoingBinaryMessage](10 millis)
          val sentMessage = ConvergenceMessage.parseFrom(sentBytes)
          sentMessage shouldBe expectedResponseEnvelope
        }
      }

      "send a correct reply envelope for an unexpected error" in new TestFixture() {
        {
          val message = HandshakeRequestMessage(reconnect = false, None)
          val envelope = ConvergenceMessage()
            .withRequestId(1)
            .withHandshakeRequest(message)

          val bytes = envelope.toByteArray
          val RequestReceived(_, cb) = connection.onIncomingMessage(bytes).success.value.value.asInstanceOf[RequestReceived]

          cb.unexpectedError(errorMessage)

          val SendUnprocessedMessage(convergenceMessage) = this.clientActor.expectMessageType[SendUnprocessedMessage](10 millis)
          connection.serializeAndSend(convergenceMessage)

          val OutgoingBinaryMessage(sentBytes) = this.connectionActor.expectMessageType[OutgoingBinaryMessage](10 millis)
          val sentMessage = ConvergenceMessage.parseFrom(sentBytes)

          sentMessage.body.error shouldBe defined
          sentMessage.getError.code shouldBe "unknown"
          sentMessage.getError.message shouldBe errorMessage
        }
      }

      "send a correct reply envelope for an expected error" in new TestFixture() {
        {
          val handshakeRequest = HandshakeRequestMessage(reconnect = false, None)
          val sentMessage = ConvergenceMessage()
            .withRequestId(1)
            .withHandshakeRequest(handshakeRequest)

          val bytes = sentMessage.toByteArray
          val RequestReceived(_, cb) = connection.onIncomingMessage(bytes).success.value.value.asInstanceOf[RequestReceived]

          cb.expectedError(code, errorMessage)

          val SendUnprocessedMessage(convergenceMessage) = this.clientActor.expectMessageType[SendUnprocessedMessage](10 millis)
          connection.serializeAndSend(convergenceMessage)

          val OutgoingBinaryMessage(sentBytes) = this.connectionActor.expectMessageType[OutgoingBinaryMessage](10 millis)
          val sentError = ConvergenceMessage.parseFrom(sentBytes)

          sentError.body.error shouldBe defined
          sentError.getError.code shouldBe code.toString
          sentError.getError.message shouldBe errorMessage
        }
      }
    }

    "set to ping" must {
      "ping within the specified interval" in {
        // FIXME this test doesn't do anything.
//        val clientActor = testKit.createTestProbe[ClientActor.Message]()
//        val connectionActor = testKit.createTestProbe[ConnectionActor.Message]()
//
//        val protoConfig = ProtocolConfiguration(
//          100 millis,
//          100 millis,
//          HeartbeatConfiguration(
//            enabled = true,
//            10 millis,
//            10 seconds))
//
//        val OutgoingBinaryMessage(replyMessage) = connectionActor.expectMessageType[OutgoingBinaryMessage](100 millis)
//        val convergenceMessage = ConvergenceMessage.parseFrom(replyMessage)
//
//        val expected = ConvergenceMessage().withPing(PingMessage())
//        convergenceMessage shouldBe expected
      }

      "timeout within the specified interval" in {
        // FIXME this test doesn't do anything.
//        val clientActor = testKit.createTestProbe[ClientActor.Message]()
//        val connectionActor = testKit.createTestProbe[ConnectionActor.Message]()
//
//        val protoConfig = ProtocolConfiguration(
//          100 millis,
//          100 millis,
//          HeartbeatConfiguration(
//            enabled = true,
//            10 millis,
//            10 millis))
//
//        clientActor.expectMessage(PongTimeout)
      }
    }

    "receiving a reply" must {
      "ignore when the reply has no request" in new TestFixture() {
        {
          val autoCreate = AutoCreateModelConfigResponseMessage()
            .withCollection(collectionId)
            .withData(ObjectValue("vid1", Map()))

          val sentMessage = ConvergenceMessage()
            .withResponseId(1)
            .withModelAutoCreateConfigResponse(autoCreate)

          val bytes = sentMessage.toByteArray
          connection.onIncomingMessage(bytes).success
        }
      }

      "resolve the request future with the proper message" in new TestFixture() {
        {
          val toSend = AutoCreateModelConfigRequestMessage(autoCreateId)
          val f = connection.request(toSend)

          val SendUnprocessedMessage(convergenceMessage) = this.clientActor.expectMessageType[SendUnprocessedMessage](10 millis)
          connection.serializeAndSend(convergenceMessage)

          val OutgoingBinaryMessage(sentBytes) = this.connectionActor.expectMessageType[OutgoingBinaryMessage](10 millis)
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
      }

      "resolve the future with a failure if an error is received" in new TestFixture() {
        {
          val toSend = AutoCreateModelConfigRequestMessage(autoCreateId)
          val f = connection.request(toSend)

          val SendUnprocessedMessage(convergenceMessage) = this.clientActor.expectMessageType[SendUnprocessedMessage](10 millis)
          connection.serializeAndSend(convergenceMessage)

          val OutgoingBinaryMessage(sentBytes) = this.connectionActor.expectMessageType[OutgoingBinaryMessage](10 millis)
          val sentMessage = ConvergenceMessage.parseFrom(sentBytes)

          val replyMessage = ErrorMessage(code.toString, errorMessage, Map("foo" -> Value(Value.Kind.StringValue("bar"))))
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
  }

  class TestFixture() {
    val clientActor: scaladsl.TestProbe[ClientActor.Message] = testKit.createTestProbe[ClientActor.Message]()
    val connectionActor: scaladsl.TestProbe[ConnectionActor.Message] = testKit.createTestProbe[ConnectionActor.Message]()

    val protoConfig: ProtocolConfiguration = ProtocolConfiguration(
      100 millis,
      100 millis,
      HeartbeatConfiguration(
        enabled = false,
        5 seconds,
        10 seconds))

    val connection = new ProtocolConnection(
      clientActor.ref,
      connectionActor.ref,
      protoConfig,
      system.scheduler,
      system.executionContext)
  }
}
