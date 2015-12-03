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
import scala.concurrent.Future
import com.convergencelabs.server.frontend.realtime.proto.ModelFqnData
import com.convergencelabs.server.domain.AuthenticationRequest
import com.convergencelabs.server.frontend.realtime.proto.AuthenticationRequestMessage
import com.convergencelabs.server.frontend.realtime.proto.PasswordAuthenticationRequestMessage
import java.net.PasswordAuthentication
import com.convergencelabs.server.domain.AuthenticationSuccess
import com.convergencelabs.server.domain.PasswordAuthRequest
import com.convergencelabs.server.datastore.domain.DomainPersistenceProvider
import com.convergencelabs.server.util.MockDomainPersistenceManagerActor
import com.convergencelabs.server.frontend.realtime.proto.AuthenticationResponseMessage

@RunWith(classOf[JUnitRunner])
class ClientActorSpec
    extends TestKit(ActorSystem("ClientActorSpec"))
    with WordSpecLike
    with BeforeAndAfterAll
    with MockitoSugar {

  override def afterAll() {
    TestKit.shutdownActorSystem(system)
  }

  "A ClientActor" when {
    "handshaking" must {
      "request a handshake with the domain when a handshake message is received" in new TestFixture(system) {
        val handshakeRequestMessage = HandshakeRequestMessage(false, None, None)
        val cb = new TestReplyCallback()
        val event = RequestReceived(handshakeRequestMessage, cb)

        clientActor.tell(event, ActorRef.noSender)
        domainManagerActor.expectMsgClass(FiniteDuration(1, TimeUnit.SECONDS), classOf[HandshakeRequest])
        domainManagerActor.reply(HandshakeSuccess("sessionId", "reconnectToken", new TestProbe(system).ref, new TestProbe(system).ref))

        val reply = Await.result(cb.result, 50 millis)
        assert(reply == HandshakeResponseMessage(true, None, Some("sessionId"), Some("reconnectToken")))
      }

      "properly handle a hanshake error form the domain" in new TestFixture(system) {
        val probeWatcher = new TestProbe(system)
        probeWatcher watch clientActor

        val handshakeRequestMessage = HandshakeRequestMessage(false, None, None)
        val cb = new TestReplyCallback()
        val event = RequestReceived(handshakeRequestMessage, cb)

        clientActor.tell(event, ActorRef.noSender)

        domainManagerActor.expectMsgClass(FiniteDuration(1, TimeUnit.SECONDS), classOf[HandshakeRequest])
        domainManagerActor.reply(HandshakeFailure("code", "string"))

        val reply = Await.result(cb.result, 50 millis)
        assert(reply == HandshakeResponseMessage(false, Some(ErrorData("code", "string")), None, None))
        probeWatcher.expectMsgClass(FiniteDuration(1, TimeUnit.SECONDS), classOf[Terminated])
      }

      "shutdown if a non handshake message is received" in new TestFixture(system) {
        val probeWatcher = new TestProbe(system)
        probeWatcher watch clientActor

        val cb = mock[ReplyCallback]
        val event = RequestReceived(CloseRealtimeModelRequestMessage("invalid message"), cb)
        clientActor.tell(event, ActorRef.noSender)

        probeWatcher.expectMsgClass(FiniteDuration(1, TimeUnit.SECONDS), classOf[Terminated])
        Mockito.verify(connection, times(1)).abort(Matchers.any())
      }

      "send a handshake failure after a timeout from the domain" in new TestFixture(system) {
        val probeWatcher = new TestProbe(system)
        probeWatcher watch clientActor

        val handshakeRequestMessage = HandshakeRequestMessage(false, None, None)
        val cb = new TestReplyCallback()
        val event = RequestReceived(handshakeRequestMessage, cb)
        clientActor.tell(event, ActorRef.noSender)

        probeWatcher.expectMsgClass(FiniteDuration(1250, TimeUnit.MILLISECONDS), classOf[Terminated])
        val HandshakeResponseMessage(success, error, sessionId, token) = Await.result(cb.result, 50 millis)

        assert(!success)

        Mockito.verify(connection, times(1)).abort(Matchers.any())
      }

      "shut down if no handshake is recieved from the client" in new TestFixture(system) {
        val probeWatcher = new TestProbe(system)
        probeWatcher watch clientActor
        probeWatcher.expectMsgClass(FiniteDuration(500, TimeUnit.MILLISECONDS), classOf[Terminated])
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
      "forward to the model manager" in new AuthenticatedClient(system) {
        val openRequest = OpenRealtimeModelRequestMessage(ModelFqnData("collection", "model"))
        val openReply = mock[ReplyCallback]
        val openEvent = RequestReceived(openRequest, openReply)
        clientActor.tell(openEvent, ActorRef.noSender)

        modelManagerActor.expectMsgClass(FiniteDuration(2, TimeUnit.SECONDS), classOf[OpenRealtimeModelRequest])
      }
    }
  }

  class TestFixture(system: ActorSystem) {
    val domainManagerActor = new TestProbe(system)

    val domainFqn = DomainFqn("namespace", "domainId")
    val protoConfig = ProtocolConfiguration(2L)
    val connection = mock[ProtocolConnection]

    val props = ClientActor.props(
      domainManagerActor.ref,
      connection,
      domainFqn,
      new FiniteDuration(250, TimeUnit.MILLISECONDS))

    val clientActor = system.actorOf(props)
  }

  class HandshookClient(system: ActorSystem) extends TestFixture(system: ActorSystem) {
    val domainActor = new TestProbe(system)
    val modelManagerActor = new TestProbe(system)

    val handshakeRequestMessage = HandshakeRequestMessage(false, None, None)
    val handshakeCallback = new TestReplyCallback()
    val handshakeEvent = RequestReceived(handshakeRequestMessage, handshakeCallback)

    clientActor.tell(handshakeEvent, ActorRef.noSender)

    domainManagerActor.expectMsgClass(FiniteDuration(1, TimeUnit.SECONDS), classOf[HandshakeRequest])
    domainManagerActor.reply(HandshakeSuccess("sessionId", "reconnectToken", domainActor.ref, modelManagerActor.ref))
    Await.result(handshakeCallback.result, 250 millis)
  }

  class AuthenticatedClient(system: ActorSystem) extends HandshookClient(system: ActorSystem) {
    val authRequestMessage = PasswordAuthenticationRequestMessage("test", "test")
    
    val authCallback = new TestReplyCallback()
    val authEvent = RequestReceived(authRequestMessage, authCallback)

    clientActor.tell(authEvent, ActorRef.noSender)

    domainActor.expectMsgClass(FiniteDuration(1, TimeUnit.SECONDS), classOf[PasswordAuthRequest])
    domainActor.reply(AuthenticationSuccess("u1", "test"))
    
    val AuthenticationResponseMessage(rId, cId) = Await.result(authCallback.result, 250 millis)
  }

  class TestReplyCallback() extends ReplyCallback {
    val p = Promise[OutgoingProtocolResponseMessage]
    def reply(message: OutgoingProtocolResponseMessage): Unit = {
      p.success(message)
    }

    def error(cause: Throwable): Unit = {
      p.failure(cause)
    }

    def result(): Future[OutgoingProtocolResponseMessage] = {
      p.future
    }
  }
}