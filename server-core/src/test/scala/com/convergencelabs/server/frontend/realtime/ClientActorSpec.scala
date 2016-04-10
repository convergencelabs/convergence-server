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
import akka.actor.PoisonPill

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

  }

  class TestFixture(system: ActorSystem) {
    val domainManagerActor = new TestProbe(system)

    val connectionActor = new TestProbe(system)

    val domainFqn = DomainFqn("namespace", "domainId")
    val protoConfig = ProtocolConfiguration(
      2 seconds,
      250 millis,
      HeartbeatConfiguration(
        false,
        0 seconds,
        0 seconds))

    val props = ClientActor.props(
      domainManagerActor.ref,
      domainFqn,
      protoConfig)

    val clientActor = system.actorOf(props)

    clientActor ! WebSocketOpened(connectionActor.ref)
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
