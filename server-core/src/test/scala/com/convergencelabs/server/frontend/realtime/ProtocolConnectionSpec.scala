package com.convergencelabs.server.frontend.realtime

import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.TimeUnit

import scala.concurrent.duration.Duration
import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.FiniteDuration
import scala.language.postfixOps

import org.json4s.NoTypeHints
import org.json4s.jackson.Serialization
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.mockito.Mockito.times
import org.scalatest.Assertions
import org.scalatest.BeforeAndAfterAll
import org.scalatest.Finders
import org.scalatest.WordSpecLike
import org.scalatest.junit.JUnitRunner
import org.scalatest.mock.MockitoSugar

import com.convergencelabs.server.ProtocolConfiguration

import akka.actor.ActorSystem
import akka.testkit.TestKit

@RunWith(classOf[JUnitRunner])
class ProtocolConnectionSpec
    extends TestKit(ActorSystem("ProtocolConnectionSpec"))
    with WordSpecLike
    with BeforeAndAfterAll
    with MockitoSugar
    with Assertions {


  implicit val formats = Serialization.formats(NoTypeHints)

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  "A ProtocolConnection" when {
    "receiving a request" must {
      "emit a request received event" in new TestFixture(system) {

        try {
          val receiver = new Receiver(connection)
          val message = HandshakeRequestMessage(false, None, None)
          val envelope = MessageEnvelope(
              OpCode.Request, 
              Some(1L), 
              Some(MessageType.Handshake), 
              MessageSerializer.decomposeBody(Some(message)))
          val json = MessageSerializer.writeJson(envelope)
          socket.fireOnMessage(json)

          val RequestReceived(x, r) = receiver.expectEventClass(10 millis, classOf[RequestReceived])
          assert(message == x)

        } finally {
          connection.close()
        }
      }
    }

    "responding to a request" must {
      "send a correct reply envelope" in new TestFixture(system) {
        val receiver = new Receiver(connection)
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
    }
  }

  class TestFixture(system: ActorSystem) {
    val protoConfig = ProtocolConfiguration(Duration.create(1, TimeUnit.SECONDS))
    val socket = Mockito.spy(new TestSocket())
    val connection = new ProtocolConnection(socket, protoConfig, false, system.scheduler, system.dispatcher)
  }

  class Receiver(connection: ProtocolConnection) {

    connection.eventHandler = receive

    private def receive: PartialFunction[ConnectionEvent, Unit] = {
      case x: Any => queue.add(x)
    }

    private val queue = new LinkedBlockingDeque[ConnectionEvent]()

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
    def send(message: String): Unit = {
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
