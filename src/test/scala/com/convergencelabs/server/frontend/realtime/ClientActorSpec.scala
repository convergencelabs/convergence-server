package com.convergencelabs.server.frontend.realtime

import java.util.concurrent.TimeUnit
import scala.concurrent.duration._
import scala.language.postfixOps
import akka.actor.ActorSystem
import akka.testkit.{ TestProbe, TestKit }
import org.json4s.JsonAST.{ JObject, JString }
import org.mockito.{ ArgumentCaptor, Mockito, Matchers }
import org.mockito.Mockito.{ verify, times }
import org.scalatest.mock.MockitoSugar
import org.scalatest.{ BeforeAndAfterAll, WordSpecLike }
import scala.concurrent.duration.FiniteDuration
import org.scalatest.junit.JUnitRunner
import org.junit.runner.RunWith
import com.convergencelabs.server.domain.DomainFqn
import com.convergencelabs.server.ProtocolConfiguration
import com.convergencelabs.server.frontend.realtime.proto.HandshakeRequestMessage
import com.convergencelabs.server.frontend.realtime.proto.OutgoingProtocolMessage
import scala.concurrent.Promise
import com.convergencelabs.server.frontend.realtime.proto.OutgoingProtocolResponseMessage
import akka.actor.ActorRef
import com.convergencelabs.server.domain.HandshakeRequest
import com.convergencelabs.server.domain.model.CloseRealtimeModelSuccess
import com.convergencelabs.server.domain.model.CloseRealtimeModelRequest
import com.convergencelabs.server.frontend.realtime.proto.CloseRealtimeModelRequestMessage
import akka.actor.Terminated
import com.convergencelabs.server.frontend.realtime.proto.OpCode
import com.convergencelabs.server.frontend.realtime.proto.HandshakeResponseMessage
import scala.util.Success
import com.convergencelabs.server.domain.HandshakeSuccess
import akka.testkit.TestProbe
import scala.concurrent.Await
import com.convergencelabs.server.frontend.realtime.proto.ErrorData
import com.convergencelabs.server.domain.HandshakeFailure
import com.convergencelabs.server.frontend.realtime.proto.OpenRealtimeModelRequestMessage
import com.convergencelabs.server.domain.model.OpenRealtimeModelRequest
import com.convergencelabs.server.domain.model.ModelFqn

@RunWith(classOf[JUnitRunner])
class ClientActorSpec(system: ActorSystem)
    extends TestKit(system)
    with WordSpecLike
    with BeforeAndAfterAll
    with MockitoSugar {

  def this() = this(ActorSystem("ClientActorSpec"))

  override def afterAll() {
    TestKit.shutdownActorSystem(system)
  }

  "A ClientActor" when {
    "handshaking" must {
      "request a handshake with the domain when a handshake message is received" in new TestFixture(system) {
        val handshakeRequestMessage = HandshakeRequestMessage(false, None, None)
        val reply = Promise[OutgoingProtocolResponseMessage]
        val event = RequestReceived(handshakeRequestMessage, reply)

        clientActor.tell(event, ActorRef.noSender)

        domainManagerActor.expectMsgClass(FiniteDuration(1, TimeUnit.SECONDS), classOf[HandshakeRequest])

        domainManagerActor.reply(HandshakeSuccess("sessionId", "reconnectToken", new TestProbe(system).ref, new TestProbe(system).ref))

        var HandshakeResponseMessage(success, error, sessionId, reconnectToken) = Await.result(reply.future, 100 millis)
        assert(success)
        assert(error == None)
        assert(sessionId == Some("sessionId"))
        assert(reconnectToken == Some("reconnectToken"))
      }

      "properly handle a hanshake error form the domain" in new TestFixture(system) {
        val probeWatcher = new TestProbe(system)
        probeWatcher watch clientActor

        val handshakeRequestMessage = HandshakeRequestMessage(false, None, None)
        val reply = Promise[OutgoingProtocolResponseMessage]
        val event = RequestReceived(handshakeRequestMessage, reply)

        clientActor.tell(event, ActorRef.noSender)

        domainManagerActor.expectMsgClass(FiniteDuration(1, TimeUnit.SECONDS), classOf[HandshakeRequest])
        domainManagerActor.reply(HandshakeFailure("code", "string"))

        var HandshakeResponseMessage(success, error, sessionId, reconnectToken) = Await.result(reply.future, 100 millis)
        assert(!success)
        assert(error == Some(ErrorData("code", "string")))
        assert(sessionId == None)
        assert(reconnectToken == None)

        probeWatcher.expectMsgClass(FiniteDuration(1, TimeUnit.SECONDS), classOf[Terminated])
      }

      "shutdown if a non handshake message is received" in new TestFixture(system) {
        val probeWatcher = new TestProbe(system)
        probeWatcher watch clientActor

        val reply = Promise[OutgoingProtocolResponseMessage]
        val event = RequestReceived(CloseRealtimeModelRequestMessage("foo", "bar"), reply)
        clientActor.tell(event, ActorRef.noSender)

        probeWatcher.expectMsgClass(FiniteDuration(1, TimeUnit.SECONDS), classOf[Terminated])
        Mockito.verify(connection, times(1)).abort(Matchers.any())
      }

      "send a handshake failure after a timeout" in new TestFixture(system) {
        val probeWatcher = new TestProbe(system)
        probeWatcher watch clientActor

        val handshakeRequestMessage = HandshakeRequestMessage(false, None, None)
        val reply = Promise[OutgoingProtocolResponseMessage]
        val event = RequestReceived(handshakeRequestMessage, reply)
        clientActor.tell(event, ActorRef.noSender)

        probeWatcher.expectMsgClass(FiniteDuration(1, TimeUnit.SECONDS), classOf[Terminated])
        var Success(HandshakeResponseMessage(success, error, sessionId, reconnectToken)) = reply.future.value.get
        assert(!success)

        Mockito.verify(connection, times(1)).abort(Matchers.any())
      }
      
      "shut down if no handshake is recieved" in new TestFixture(system) {
        val probeWatcher = new TestProbe(system)
        probeWatcher watch clientActor
        probeWatcher.expectMsgClass(FiniteDuration(2, TimeUnit.SECONDS), classOf[Terminated])
        Mockito.verify(connection, times(1)).abort(Matchers.any())
      }
    }

    "recieivng a connection event" must {
      "shutdown when a ConnectionClosed event is received" in new TestFixture(system) {
        val probeWatcher = new TestProbe(system)
        probeWatcher watch clientActor
        clientActor.tell(ConnectionClosed(), ActorRef.noSender)
        probeWatcher.expectMsgClass(FiniteDuration(1, TimeUnit.SECONDS), classOf[Terminated])
      }

      "shutdown when a ConnectionDropped event is received" in new TestFixture(system) {
        val probeWatcher = new TestProbe(system)
        probeWatcher watch clientActor
        clientActor.tell(ConnectionDropped(), ActorRef.noSender)
        probeWatcher.expectMsgClass(FiniteDuration(1, TimeUnit.SECONDS), classOf[Terminated])
      }

      "shutdown when a ConnectionError event is received" in new TestFixture(system) {
        val probeWatcher = new TestProbe(system)
        probeWatcher watch clientActor
        clientActor.tell(ConnectionError("error"), ActorRef.noSender)
        probeWatcher.expectMsgClass(FiniteDuration(1, TimeUnit.SECONDS), classOf[Terminated])
      }
    }
    
    "recieving an open model message" must {
      "forward to the model manager" in new HandshookClient(system) {
        val openRequest = OpenRealtimeModelRequestMessage(ModelFqn("collection", "model"))
        val openReply = Promise[OutgoingProtocolResponseMessage]
        val openEvent = RequestReceived(openRequest, openReply)
        clientActor.tell(openEvent, ActorRef.noSender)
        
        modelManagerActor.expectMsgClass(FiniteDuration(1, TimeUnit.SECONDS), classOf[OpenRealtimeModelRequest])
      }
    }
  }

  class TestFixture(system: ActorSystem) {
    val domainManagerActor = new TestProbe(system)
    val modelManagerActor = new TestProbe(system)

    val domainFqn = DomainFqn("namespace", "domainId")
    val protoConfig = ProtocolConfiguration(2L)
    val connection = mock[ProtocolConnection]

    val props = ClientActor.props(
      domainManagerActor.ref,
      connection,
      domainFqn)

    val clientActor = system.actorOf(props)
  }

  class HandshookClient(system: ActorSystem) extends TestFixture(system: ActorSystem) {
    val handshakeRequestMessage = HandshakeRequestMessage(false, None, None)
    val handshakeReply = Promise[OutgoingProtocolResponseMessage]
    val handshakeEvent = RequestReceived(handshakeRequestMessage, handshakeReply)

    clientActor.tell(handshakeEvent, ActorRef.noSender)

    domainManagerActor.expectMsgClass(FiniteDuration(1, TimeUnit.SECONDS), classOf[HandshakeRequest])
    domainManagerActor.reply(HandshakeSuccess("sessionId", "reconnectToken", domainManagerActor.ref, modelManagerActor.ref))
    
    Await.result(handshakeReply.future, 100 millis)
  }
}