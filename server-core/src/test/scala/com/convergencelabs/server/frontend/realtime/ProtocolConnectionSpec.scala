package com.convergencelabs.server.frontend.realtime

import java.util.concurrent.LinkedBlockingDeque
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.FiniteDuration
import scala.language.postfixOps
import org.json4s.JsonAST.JString
import org.json4s.NoTypeHints
import org.json4s.jackson.Serialization
import org.junit.runner.RunWith
import org.mockito.{ Matchers => MockitoMatchers }
import org.mockito.Mockito
import org.mockito.Mockito.times
import org.scalatest.Assertions
import org.scalatest.BeforeAndAfterAll
import org.scalatest.Finders
import org.scalatest.Matchers
import org.scalatest.OptionValues.convertOptionToValuable
import org.scalatest.TryValues.convertTryToSuccessOrFailure
import org.scalatest.WordSpecLike
import org.scalatest.junit.JUnitRunner
import org.scalatest.mock.MockitoSugar
import com.convergencelabs.server.HeartbeatConfiguration
import com.convergencelabs.server.ProtocolConfiguration
import com.convergencelabs.server.util.concurrent.UnexpectedErrorException
import akka.actor.ActorSystem
import akka.testkit.TestKit
import org.json4s.JsonAST.JInt
import org.json4s.JsonAST.JObject
import com.convergencelabs.server.domain.model.data.ObjectValue

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
      "send the correct message envelope" in new TestFixture {
        val toSend = OperationAcknowledgementMessage("id", 4, 5)
        connection.send(toSend)

        val sentMessage = socket.expectSentMessage(10 millis)
        val sentEnvelope = MessageSerializer.readJson[MessageEnvelope](sentMessage)

        sentEnvelope.q shouldBe None
        sentEnvelope.b shouldBe toSend
      }
    }

    "sending a request message" must {
      "send the correct message envelope" in new TestFixture {
        val toSend = ModelDataRequestMessage(collectionId, modelId)
        connection.request(toSend)

        val sentMessage = socket.expectSentMessage(10 millis)
        val sentEnvelope = MessageSerializer.readJson[MessageEnvelope](sentMessage)

        sentEnvelope.q shouldBe defined
        sentEnvelope.b shouldBe toSend
      }
    }

    "receiving a socket event" must {
      "fire a ConnectionClosed event when the socket is closed" in new TestFixture {
        socket.fireOnClosed()
        receiver.expectEvent(10 millis, ConnectionClosed)
      }

      "fire a ConnectionDropped event when the socket is closed" in new TestFixture {
        socket.fireOnDropped()
        receiver.expectEvent(10 millis, ConnectionDropped)
      }

      "fire a ConnectionError event when the socket is closed" in new TestFixture {
        val message = "error message"
        socket.fireOnError(message)
        receiver.expectEvent(10 millis, ConnectionError(message))
      }
    }

    "closing a connection" must {
      "close the socket normally" in new TestFixture {
        connection.close()
        Mockito.verify(socket, times(1)).close(MockitoMatchers.anyString())
      }
    }

    "aborting a connection" must {
      "abort the socket with the specifed reason" in new TestFixture {
        val reason = "test abort"
        connection.abort(reason)
        Mockito.verify(socket, times(1)).abort(reason)
      }
    }

    "receiving an invalid message" must {
      "emit a error event and abort the connection if an invalid json is recieved" in new TestFixture {
        socket.fireOnMessage("{")
        val ConnectionError(x) = receiver.expectEventClass(10 millis, classOf[ConnectionError])
        Mockito.verify(socket, times(1)).abort(MockitoMatchers.anyString())
      }
    }

    "receiving a request" must {
      "emit a request received event" in new TestFixture {
        val message = HandshakeRequestMessage(false, None)
        val envelope = MessageEnvelope(message, Some(1L), None)
        val json = MessageSerializer.writeJson(envelope)
        socket.fireOnMessage(json)

        val RequestReceived(x, r) = receiver.expectEventClass(10 millis, classOf[RequestReceived])
        x shouldBe message
      }
    }

    "receiving a normal message" must {
      "emit a message received event" in new TestFixture {
        val message = OperationSubmissionMessage(session, 1L, 2L, CompoundOperationData(List()))
        val envelope = MessageEnvelope(
          message,
          None,
          None)
        val json = MessageSerializer.writeJson(envelope)
        socket.fireOnMessage(json)

        val MessageReceived(x) = receiver.expectEventClass(10 millis, classOf[MessageReceived])
        x shouldBe message
      }

      "emit a error event and abort the connection if a message with a request Id" in new TestFixture {
        val message = OperationSubmissionMessage(session, 1L, 2L, CompoundOperationData(List()))
        val envelope = MessageEnvelope(
          message,
          Some(1L),
          None)
        val json = MessageSerializer.writeJson(envelope)
        socket.fireOnMessage(json)

        val ConnectionError(x) = receiver.expectEventClass(10 millis, classOf[ConnectionError])
        Mockito.verify(socket, times(1)).abort(MockitoMatchers.anyString())
      }
    }

    "receiving a ping message" must {
      "respond with a pong" in new TestFixture {
        val envelope = MessageEnvelope(
          PingMessage(),
          None,
          None)
        val json = MessageSerializer.writeJson(envelope)
        socket.fireOnMessage(json)

        val sentMessage = socket.expectSentMessage(10 millis)
        val sentEnvelope = MessageSerializer.readJson[MessageEnvelope](sentMessage)

        sentEnvelope shouldBe MessageEnvelope(PongMessage(), None, None)
      }
    }

    "responding to a request" must {
      "send a correct reply envelope for a success" in new TestFixture {
        val message = HandshakeRequestMessage(false, None)
        val envelope = MessageEnvelope(message, Some(1L), None)

        val json = MessageSerializer.writeJson(envelope)
        socket.fireOnMessage(json)
        val RequestReceived(m, cb) = receiver.expectEventClass(10 millis, classOf[RequestReceived])

        val response = HandshakeResponseMessage(true, None, Some("foo"), Some("bar"), None, None)
        cb.reply(response)

        val responseEnvelop = MessageEnvelope(response, None, Some(1L))
        Mockito.verify(socket, times(1)).send(MessageSerializer.writeJson(responseEnvelop))
        connection.close()
      }

      "send a correct reply envelope for an unexpected error" in new TestFixture {
        val message = HandshakeRequestMessage(false, None)
        val envelope = MessageEnvelope(message, Some(1L), None)

        val json = MessageSerializer.writeJson(envelope)
        socket.fireOnMessage(json)
        val RequestReceived(m, cb) = receiver.expectEventClass(10 millis, classOf[RequestReceived])

        cb.unexpectedError(details)

        val sentMessage = socket.expectSentMessage(20 millis)
        val sentEnvelope = MessageEnvelope(sentMessage).success.value
        val errorMessage = sentEnvelope.b.asInstanceOf[ErrorMessage]

        errorMessage.c shouldBe "unknown"
        errorMessage.d shouldBe details
      }

      "send a correct reply envelope for an expected error" in new TestFixture {
        val message = HandshakeRequestMessage(false, None)
        val envelope = MessageEnvelope(message, Some(1L), None)

        val json = MessageSerializer.writeJson(envelope)
        socket.fireOnMessage(json)
        val RequestReceived(m, cb) = receiver.expectEventClass(10 millis, classOf[RequestReceived])

        cb.expectedError(code, details)

        val sentMessage = socket.expectSentMessage(20 millis)
        val sentEnvelope = MessageEnvelope(sentMessage).success.value

        val errorMessage = sentEnvelope.b.asInstanceOf[ErrorMessage]
        errorMessage.c shouldBe code
        errorMessage.d shouldBe details
      }
    }

    "set to ping" must {
      "ping within the specified interval" in {
        val protoConfig = ProtocolConfiguration(
          100 millis,
          HeartbeatConfiguration(
            true,
            10 millis,
            10 seconds))

        val socket = Mockito.spy(new TestSocket())
        val connection = new ProtocolConnection(
          socket,
          protoConfig,
          system.scheduler,
          system.dispatcher)
        connection.eventHandler = { case _ => }

        val message = socket.expectSentMessage(50 millis)

        val sentEnvelope = MessageSerializer.readJson[MessageEnvelope](message)
        sentEnvelope shouldBe MessageEnvelope(PingMessage(), None, None)
      }

      "timeout within the specified interval" in {
        val protoConfig = ProtocolConfiguration(
          100 millis,
          HeartbeatConfiguration(
            true,
            10 millis,
            10 millis))

        val socket = Mockito.spy(new TestSocket())
        val connection = new ProtocolConnection(
          socket,
          protoConfig,
          system.scheduler,
          system.dispatcher)
        connection.eventHandler = { case _ => }

        val receiver = new Receiver(connection)
        receiver.expectEvent(100 millis, ConnectionDropped)
      }
    }

    "receiving a reply" must {

      "ignore when the reply has no request" in new TestFixture {
        val message = ModelDataResponseMessage(ObjectValue("id", Map()))
        val envelope = MessageEnvelope(message, None, Some(1L))

        val json = MessageSerializer.writeJson(envelope)
        socket.fireOnMessage(json)
      }

      "resolve the request future with the proper message" in new TestFixture {
        val toSend = ModelDataRequestMessage(collectionId, modelId)
        val f = connection.request(toSend)

        val sentMessage = socket.expectSentMessage(10 millis)
        val sentEnvelope = MessageSerializer.readJson[MessageEnvelope](sentMessage)

        val replyMessage = ModelDataResponseMessage(ObjectValue("id", Map()))
        val replyEnvelope = MessageEnvelope(replyMessage, None, sentEnvelope.q)

        val replyJson = MessageSerializer.writeJson(replyEnvelope)
        socket.fireOnMessage(replyJson)

        val response = Await.result(f, 10 millis)

        response shouldBe replyMessage
      }

      "resolve the future with a failure if an error is recieved" in new TestFixture {
        val toSend = ModelDataRequestMessage(collectionId, modelId)
        val f = connection.request(toSend)

        val sentMessage = socket.expectSentMessage(10 millis)
        val sentEnvelope = MessageSerializer.readJson[MessageEnvelope](sentMessage)

        val replyMessage = ErrorMessage(code, details)
        val replyEnvelope = MessageEnvelope(replyMessage, None, sentEnvelope.q)

        val replyJson = MessageSerializer.writeJson(replyEnvelope)
        socket.fireOnMessage(replyJson)

        Await.ready(f, 10 millis)
        val cause = f.value.get.failure.exception
        cause shouldBe a[ClientErrorResponseException]

        val errorException = cause.asInstanceOf[ClientErrorResponseException]

        errorException.message shouldBe details
      }
    }
  }

  class TestFixture {
    val protoConfig = ProtocolConfiguration(
      100 millis,
      HeartbeatConfiguration(
        false,
        5 seconds,
        10 seconds))

    val socket = Mockito.spy(new TestSocket())
    val connection = new ProtocolConnection(
      socket,
      protoConfig,
      system.scheduler,
      system.dispatcher)
    val receiver = new Receiver(connection)
  }

  class Receiver(connection: ProtocolConnection) {

    connection.eventHandler = receive
    connection.ready()

    private def receive: PartialFunction[ConnectionEvent, Unit] = {
      case x: Any => queue.add(x)
    }

    private val queue = new LinkedBlockingDeque[ConnectionEvent]()

    // scalastyle:off null
    def expectEvent(max: FiniteDuration, e: ConnectionEvent): Unit = {
      val o = receiveOne(max)
      assert(o ne null, s"timeout ($max) during expectMsgClass waiting for $e")
      assert(e == o)
    }
    // scalastyle:on null

    def expectEventClass[C](max: FiniteDuration, c: Class[C]): C = expectEventClassInternal(max, c)

    // scalastyle:off null
    private def expectEventClassInternal[C](max: FiniteDuration, c: Class[C]): C = {
      val o = receiveOne(max)
      assert(o ne null, s"timeout ($max) during expectMsgClass waiting for $c")
      assert(c isInstance o, s"expected $c, found ${o.getClass}")
      o.asInstanceOf[C]
    }
    // scalastyle:on null

    def receiveOne(max: Duration): AnyRef = {
      val message =
        if (max == 0.seconds) {
          queue.pollFirst
        } else if (max.isFinite) {
          queue.pollFirst(max.length, max.unit)
        } else {
          queue.takeFirst
        }

      message
    }
  }

  class TestSocket() extends ConvergenceServerSocket {
    private val queue = new LinkedBlockingDeque[String]()

    def send(message: String): Unit = {
      queue.add(message)
    }

    def expectSentMessage(max: Duration): String = {
      val message =
        if (max == 0.seconds) {
          queue.pollFirst
        } else if (max.isFinite) {
          queue.pollFirst(max.length, max.unit)
        } else {
          queue.takeFirst
        }

      message
    }

    var isOpen: Boolean = true

    def close(reason: String): Unit = {
    }

    def abort(reason: String): Unit = {
    }

    def dispose(): Unit = {
    }
  }
}
