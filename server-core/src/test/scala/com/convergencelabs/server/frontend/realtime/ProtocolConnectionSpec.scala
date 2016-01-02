package com.convergencelabs.server.frontend.realtime

import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.TimeUnit
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.FiniteDuration
import scala.language.postfixOps
import org.json4s.JsonAST.JString
import org.json4s.NoTypeHints
import org.json4s.jackson.Serialization
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.{ Matchers => MockitoMatchers }
import org.mockito.Mockito
import org.mockito.Mockito.times
import org.scalatest.Assertions
import org.scalatest.BeforeAndAfterAll
import org.scalatest.Matchers
import org.scalatest.OptionValues._
import org.scalatest.TryValues._
import org.scalatest.WordSpecLike
import org.scalatest.junit.JUnitRunner
import org.scalatest.mock.MockitoSugar
import com.convergencelabs.server.HeartbeatConfiguration
import com.convergencelabs.server.ProtocolConfiguration
import com.convergencelabs.server.util.concurrent.UnexpectedErrorException
import akka.actor.ActorSystem
import akka.testkit.TestKit
import com.convergencelabs.server.util.concurrent.UnexpectedErrorException

@RunWith(classOf[JUnitRunner])
class ProtocolConnectionSpec
    extends TestKit(ActorSystem("ProtocolConnectionSpec"))
    with WordSpecLike
    with BeforeAndAfterAll
    with Matchers
    with MockitoSugar
    with Assertions {

  implicit val formats = Serialization.formats(NoTypeHints)
  implicit val ec = system.dispatcher

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

        sentEnvelope.opCode shouldBe OpCode.Normal
        sentEnvelope.reqId shouldBe None
        sentEnvelope.`type` shouldBe MessageSerializer.typeOfOutgoingMessage(Some(toSend))

        val sent = MessageSerializer.extractBody(sentEnvelope.body.get, classOf[OperationAcknowledgementMessage])
        sent shouldBe toSend
      }
    }

    "sending a request message" must {
      "send the correct message envelope" in new TestFixture {
        val toSend = ModelDataRequestMessage(ModelFqnData("c", "m"))
        connection.request(toSend)

        val sentMessage = socket.expectSentMessage(10 millis)
        val sentEnvelope = MessageSerializer.readJson[MessageEnvelope](sentMessage)

        sentEnvelope.opCode shouldBe OpCode.Request
        sentEnvelope.reqId shouldBe defined
        sentEnvelope.`type` shouldBe MessageSerializer.typeOfOutgoingMessage(Some(toSend))

        val sent = MessageSerializer.extractBody(sentEnvelope.body.get, classOf[ModelDataRequestMessage])
        sent shouldBe toSend
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
        val message = HandshakeRequestMessage(false, None, None)
        val envelope = MessageEnvelope(
          OpCode.Request,
          Some(1L),
          Some(MessageType.Handshake),
          MessageSerializer.decomposeBody(Some(message)))
        val json = MessageSerializer.writeJson(envelope)
        socket.fireOnMessage(json)

        val RequestReceived(x, r) = receiver.expectEventClass(10 millis, classOf[RequestReceived])
        x shouldBe message
      }

      "emit a error event and abort the connection if a message was not a request" in new TestFixture {
        val message = OperationSubmissionMessage("session", 1L, 2L, CompoundOperationData(List()))
        val envelope = MessageEnvelope(
          OpCode.Request,
          Some(1L),
          Some(MessageType.OperationSubmission),
          MessageSerializer.decomposeBody(Some(message)))
        val json = MessageSerializer.writeJson(envelope)
        socket.fireOnMessage(json)

        val ConnectionError(x) = receiver.expectEventClass(10 millis, classOf[ConnectionError])
        Mockito.verify(socket, times(1)).abort(MockitoMatchers.anyString())
      }
    }

    "receiving a normal message" must {
      "emit a message received event" in new TestFixture {
        val message = OperationSubmissionMessage("session", 1L, 2L, CompoundOperationData(List()))
        val envelope = MessageEnvelope(
          OpCode.Normal,
          None,
          Some(MessageType.OperationSubmission),
          MessageSerializer.decomposeBody(Some(message)))
        val json = MessageSerializer.writeJson(envelope)
        socket.fireOnMessage(json)

        val MessageReceived(x) = receiver.expectEventClass(10 millis, classOf[MessageReceived])
        x shouldBe message
      }

      "emit a error event and abort the connection if an invalid message body is recieved" in new TestFixture {
        val message = HandshakeRequestMessage(false, None, None)
        val envelope = MessageEnvelope(
          OpCode.Normal,
          None,
          Some(MessageType.Handshake),
          MessageSerializer.decomposeBody(Some(message)))
        val json = MessageSerializer.writeJson(envelope)
        socket.fireOnMessage(json)

        val ConnectionError(x) = receiver.expectEventClass(10 millis, classOf[ConnectionError])
        Mockito.verify(socket, times(1)).abort(MockitoMatchers.anyString())
      }

      "emit a error event and abort the connection if a message with a request Id" in new TestFixture {
        val message = OperationSubmissionMessage("session", 1L, 2L, CompoundOperationData(List()))
        val envelope = MessageEnvelope(
          OpCode.Normal,
          Some(1L),
          Some(MessageType.OperationSubmission),
          MessageSerializer.decomposeBody(Some(message)))
        val json = MessageSerializer.writeJson(envelope)
        socket.fireOnMessage(json)

        val ConnectionError(x) = receiver.expectEventClass(10 millis, classOf[ConnectionError])
        Mockito.verify(socket, times(1)).abort(MockitoMatchers.anyString())
      }
    }

    "receiving a ping message" must {
      "respond with a pong" in new TestFixture {
        val envelope = MessageEnvelope(
          OpCode.Ping,
          None,
          None,
          None)
        val json = MessageSerializer.writeJson(envelope)
        socket.fireOnMessage(json)

        val sentMessage = socket.expectSentMessage(10 millis)
        val sentEnvelope = MessageSerializer.readJson[MessageEnvelope](sentMessage)

        sentEnvelope shouldBe MessageEnvelope(OpCode.Pong, None, None, None)
      }
    }

    "responding to a request" must {
      "send a correct reply envelope for a success" in new TestFixture {
        val message = HandshakeRequestMessage(false, None, None)
        val envelope = MessageEnvelope(
          OpCode.Request,
          Some(1L),
          Some(MessageType.Handshake),
          MessageSerializer.decomposeBody(Some(message)))

        val json = MessageSerializer.writeJson(envelope)
        socket.fireOnMessage(json)
        val RequestReceived(m, cb) = receiver.expectEventClass(10 millis, classOf[RequestReceived])

        val response = HandshakeResponseMessage(true, None, Some("foo"), Some("bar"))
        cb.reply(response)

        val responseEnvelop = MessageEnvelope(1L, response)
        Mockito.verify(socket, times(1)).send(MessageSerializer.writeJson(responseEnvelop))
        connection.close()
      }

      "send a correct reply envelope for an unreccognized exception error" in new TestFixture {
        val message = HandshakeRequestMessage(false, None, None)
        val envelope = MessageEnvelope(
          OpCode.Request,
          Some(1L),
          Some(MessageType.Handshake),
          MessageSerializer.decomposeBody(Some(message)))

        val json = MessageSerializer.writeJson(envelope)
        socket.fireOnMessage(json)
        val RequestReceived(m, cb) = receiver.expectEventClass(10 millis, classOf[RequestReceived])

        val exception = new IllegalArgumentException("message")
        cb.error(exception)

        val sentMessage = socket.expectSentMessage(20 millis)
        val sentEnvelope = MessageEnvelope(sentMessage).success.value
        sentEnvelope.opCode shouldBe OpCode.Reply
        sentEnvelope.`type`.value shouldBe MessageType.Error

        val sentBody = MessageSerializer.extractBody(sentEnvelope.body.get, classOf[ErrorMessage])

        sentBody.code shouldBe "unknown"
        sentBody.details shouldBe exception.getMessage
      }

      "send a correct reply envelope for an unexpected exception error" in new TestFixture {
        val message = HandshakeRequestMessage(false, None, None)
        val envelope = MessageEnvelope(
          OpCode.Request,
          Some(1L),
          Some(MessageType.Handshake),
          MessageSerializer.decomposeBody(Some(message)))

        val json = MessageSerializer.writeJson(envelope)
        socket.fireOnMessage(json)
        val RequestReceived(m, cb) = receiver.expectEventClass(10 millis, classOf[RequestReceived])

        val excpetion = new UnexpectedErrorException("code", "details")
        cb.error(excpetion)

        val sentMessage = socket.expectSentMessage(20 millis)
        val sentEnvelope = MessageEnvelope(sentMessage).success.value
        sentEnvelope.opCode shouldBe OpCode.Reply
        sentEnvelope.`type`.value shouldBe MessageType.Error

        val sentBody = MessageSerializer.extractBody(sentEnvelope.body.get, classOf[ErrorMessage])
        sentBody.code shouldBe excpetion.code
        sentBody.details shouldBe excpetion.details
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

        val message = socket.expectSentMessage(50 millis)

        val sentEnvelope = MessageSerializer.readJson[MessageEnvelope](message)
        sentEnvelope shouldBe MessageEnvelope(OpCode.Ping, None, None, None)
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

        val receiver = new Receiver(connection)
        receiver.expectEvent(100 millis, ConnectionDropped)
      }
    }

    "receiving a reply" must {

      "ignore when the reply has no request" in new TestFixture {
        val message = HandshakeResponseMessage(true, None, Some(""), Some(""))
        val envelope = MessageEnvelope(
          OpCode.Reply,
          Some(1L),
          None,
          MessageSerializer.decomposeBody(Some(message)))

        val json = MessageSerializer.writeJson(envelope)
        socket.fireOnMessage(json)
      }

      "resolve the request future with the proper message" in new TestFixture {
        val toSend = ModelDataRequestMessage(ModelFqnData("c", "m"))
        val f = connection.request(toSend)

        val sentMessage = socket.expectSentMessage(10 millis)
        val sentEnvelope = MessageSerializer.readJson[MessageEnvelope](sentMessage)

        val replyMessage = ModelDataResponseMessage(JString(""))
        val replyEnvelope = MessageEnvelope(
          OpCode.Reply,
          sentEnvelope.reqId,
          None,
          MessageSerializer.decomposeBody(Some(replyMessage)))

        val replyJson = MessageSerializer.writeJson(replyEnvelope)
        socket.fireOnMessage(replyJson)

        val response = Await.result(f, 10 millis)

        response shouldBe replyMessage
      }

      "resolve the future with a failure if an error is recieved" in new TestFixture {
        val toSend = ModelDataRequestMessage(ModelFqnData("c", "m"))
        val f = connection.request(toSend)

        val sentMessage = socket.expectSentMessage(10 millis)
        val sentEnvelope = MessageSerializer.readJson[MessageEnvelope](sentMessage)

        val replyMessage = ErrorMessage("code", "details")
        val replyEnvelope = MessageEnvelope(
          OpCode.Reply,
          sentEnvelope.reqId,
          Some(MessageType.Error),
          MessageSerializer.decomposeBody(Some(replyMessage)))

        val replyJson = MessageSerializer.writeJson(replyEnvelope)
        socket.fireOnMessage(replyJson)

        Await.ready(f, 10 millis)
        val cause = f.value.get.failure.exception
        cause shouldBe a[UnexpectedErrorException]

        val errorException = cause.asInstanceOf[UnexpectedErrorException]

        errorException.code shouldBe "code"
        errorException.details shouldBe "details"
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

    private def receive: PartialFunction[ConnectionEvent, Unit] = {
      case x: Any => queue.add(x)
    }

    private val queue = new LinkedBlockingDeque[ConnectionEvent]()

    def expectEvent(max: FiniteDuration, e: ConnectionEvent): Unit = {
      val o = receiveOne(max)
      assert(o ne null, s"timeout ($max) during expectMsgClass waiting for $e")
      assert(e == o)
    }

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
