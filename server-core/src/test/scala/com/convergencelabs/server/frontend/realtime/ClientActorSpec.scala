package com.convergencelabs.server.frontend.realtime

import java.util.concurrent.TimeUnit

import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.Promise
import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.FiniteDuration
import scala.language.postfixOps

import org.junit.runner.RunWith
import org.mockito.Matchers
import org.mockito.Mockito
import org.mockito.Mockito.times
import org.scalatest.BeforeAndAfterAll
import org.scalatest.Finders
import org.scalatest.WordSpecLike
import org.scalatest.junit.JUnitRunner
import org.scalatest.mock.MockitoSugar

import com.convergencelabs.server.HeartbeatConfiguration
import com.convergencelabs.server.ProtocolConfiguration
import com.convergencelabs.server.domain.AuthenticationSuccess
import com.convergencelabs.server.domain.DomainFqn
import com.convergencelabs.server.domain.HandshakeFailure
import com.convergencelabs.server.domain.HandshakeRequest
import com.convergencelabs.server.domain.HandshakeSuccess
import com.convergencelabs.server.domain.PasswordAuthRequest
import com.convergencelabs.server.domain.model.OpenRealtimeModelRequest

import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Terminated
import akka.testkit.TestKit
import akka.testkit.TestProbe

// scalastyle:off magic.number
@RunWith(classOf[JUnitRunner])
class ClientActorSpec
    extends TestKit(ActorSystem("ClientActorSpec"))
    with WordSpecLike
    with BeforeAndAfterAll
    with MockitoSugar {

  val SessionId = "sessionId"
  val RecconnectToken = "secconnectToken"

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  "A ClientActor" when {
    "handshaking" must {
      "request a handshake with the domain when a handshake message is received" in new TestFixture(system) {
        val handshakeRequestMessage = HandshakeRequestMessage(false, None)
        val cb = new TestReplyCallback()
        val event = RequestReceived(handshakeRequestMessage, cb)

        clientActor.tell(event, ActorRef.noSender)
        domainManagerActor.expectMsgClass(FiniteDuration(1, TimeUnit.SECONDS), classOf[HandshakeRequest])
        domainManagerActor.reply(
            HandshakeSuccess(SessionId, RecconnectToken, new TestProbe(system).ref, new TestProbe(system).ref, new TestProbe(system).ref))

        val reply = Await.result(cb.result, 50 millis)
        assert(reply == HandshakeResponseMessage(true, None, Some(SessionId), Some(RecconnectToken), None, Some(ProtocolConfigData(true))))

      }

      "properly handle a hanshake error form the domain" in new TestFixture(system) {
        val probeWatcher = new TestProbe(system)
        probeWatcher watch clientActor

        val handshakeRequestMessage = HandshakeRequestMessage(false, None)
        val cb = new TestReplyCallback()
        val event = RequestReceived(handshakeRequestMessage, cb)

        clientActor.tell(event, ActorRef.noSender)

        domainManagerActor.expectMsgClass(FiniteDuration(1, TimeUnit.SECONDS), classOf[HandshakeRequest])
        domainManagerActor.reply(HandshakeFailure("code", "string"))

        val reply = Await.result(cb.result, 50 millis)
        assert(reply == HandshakeResponseMessage(false, Some(ErrorData("code", "string")), None, None, Some(true), None))
        probeWatcher.expectMsgClass(FiniteDuration(1, TimeUnit.SECONDS), classOf[Terminated])
      }

      "shutdown if a non handshake message is received" in new TestFixture(system) {
        val probeWatcher = new TestProbe(system)
        probeWatcher watch clientActor

        val cb = mock[ReplyCallback]
        val event = RequestReceived(CloseRealtimeModelRequestMessage("invalid message"), cb)
        clientActor.tell(event, ActorRef.noSender)

        probeWatcher.expectMsgClass(FiniteDuration(1, TimeUnit.SECONDS), classOf[Terminated])
//        Mockito.verify(connection, times(1)).abort(Matchers.any())
      }

      "send a handshake failure after a timeout from the domain" in new TestFixture(system) {
        val probeWatcher = new TestProbe(system)
        probeWatcher watch clientActor

        val handshakeRequestMessage = HandshakeRequestMessage(false, None)
        val cb = new TestReplyCallback()
        val event = RequestReceived(handshakeRequestMessage, cb)
        clientActor.tell(event, ActorRef.noSender)

        probeWatcher.expectMsgClass(FiniteDuration(1250, TimeUnit.MILLISECONDS), classOf[Terminated])
        val HandshakeResponseMessage(success, error, sessionId, token, retryOk, config) = Await.result(cb.result, 50 millis)

        assert(!success)

        //Mockito.verify(connection, times(1)).abort(Matchers.any())
      }

      "shut down if no handshake is recieved from the client" in new TestFixture(system) {
        val probeWatcher = new TestProbe(system)
        probeWatcher watch clientActor
        probeWatcher.expectMsgClass(FiniteDuration(500, TimeUnit.MILLISECONDS), classOf[Terminated])
        //Mockito.verify(connection, times(1)).abort(Matchers.any())
      }
    }

    "recieivng a connection event" must {
      "shutdown when a ConnectionClosed event is received" in new TestFixture(system) {
        val probeWatcher = new TestProbe(system)
        probeWatcher watch clientActor
        //clientActor.tell(ConnectionClosed, ActorRef.noSender)
        probeWatcher.expectMsgClass(FiniteDuration(1, TimeUnit.SECONDS), classOf[Terminated])
      }

      "shutdown when a ConnectionDropped event is received" in new TestFixture(system) {
        val probeWatcher = new TestProbe(system)
        probeWatcher watch clientActor
        //clientActor.tell(ConnectionDropped, ActorRef.noSender)
        probeWatcher.expectMsgClass(FiniteDuration(1, TimeUnit.SECONDS), classOf[Terminated])
      }

      "shutdown when a ConnectionError event is received" in new TestFixture(system) {
        val probeWatcher = new TestProbe(system)
        probeWatcher watch clientActor
        //clientActor.tell(ConnectionError("error"), ActorRef.noSender)
        probeWatcher.expectMsgClass(FiniteDuration(1, TimeUnit.SECONDS), classOf[Terminated])
      }
    }

    "recieving an open model message" must {
      "forward to the model manager" in new AuthenticatedClient(system) {
        val openRequest = OpenRealtimeModelRequestMessage("collection", "model", true)
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
    val protoConfig = ProtocolConfiguration(2 seconds,
      HeartbeatConfiguration(
        false,
        0 seconds,
        0 seconds))

    val connection = mock[ProtocolConnection]

//    val props = ClientActor.props(
//      domainManagerActor.ref,
//      connection,
//      domainFqn,
//      new FiniteDuration(250, TimeUnit.MILLISECONDS))

    val clientActor = system.actorOf(null)
  }

  class HandshookClient(system: ActorSystem) extends TestFixture(system: ActorSystem) {
    val domainActor = new TestProbe(system)
    val modelManagerActor = new TestProbe(system)
    val userServiceActor = new TestProbe(system)

    val handshakeRequestMessage = HandshakeRequestMessage(false, None)
    val handshakeCallback = new TestReplyCallback()
    val handshakeEvent = RequestReceived(handshakeRequestMessage, handshakeCallback)

    clientActor.tell(handshakeEvent, ActorRef.noSender)

    domainManagerActor.expectMsgClass(FiniteDuration(1, TimeUnit.SECONDS), classOf[HandshakeRequest])
    domainManagerActor.reply(HandshakeSuccess(SessionId, RecconnectToken, domainActor.ref, modelManagerActor.ref, userServiceActor.ref))
    Await.result(handshakeCallback.result, 250 millis)
  }

  class AuthenticatedClient(system: ActorSystem) extends HandshookClient(system: ActorSystem) {
    val authRequestMessage = PasswordAuthRequestMessage("testuser", "testpass")

    val authCallback = new TestReplyCallback()
    val authEvent = RequestReceived(authRequestMessage, authCallback)

    clientActor.tell(authEvent, ActorRef.noSender)

    domainActor.expectMsgClass(FiniteDuration(1, TimeUnit.SECONDS), classOf[PasswordAuthRequest])
    domainActor.reply(AuthenticationSuccess("u1", "test"))

    val AuthenticationResponseMessage(rId, cId) = Await.result(authCallback.result, 250 millis)
  }

  class TestReplyCallback() extends ReplyCallback {
    val p = Promise[OutgoingProtocolResponseMessage]

    def result(): Future[OutgoingProtocolResponseMessage] = {
      p.future
    }

    def reply(message: OutgoingProtocolResponseMessage): Unit = {
      p.success(message)
    }

    def unknownError(): Unit = {
      p.failure(new IllegalStateException())
    }

    def unexpectedError(details: String): Unit = {
      p.failure(new IllegalStateException())
    }

    def expectedError(code: String, details: String): Unit = {
      p.failure(new IllegalStateException())
    }
  }
}
