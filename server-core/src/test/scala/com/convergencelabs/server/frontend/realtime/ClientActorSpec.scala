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
import org.scalatest.mockito.MockitoSugar
import com.convergencelabs.server.HeartbeatConfiguration
import com.convergencelabs.server.ProtocolConfiguration
import com.convergencelabs.server.domain.AuthenticationSuccess
import com.convergencelabs.server.domain.DomainFqn
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
import com.convergencelabs.server.domain.model.SessionKey
import akka.http.scaladsl.model.RemoteAddress
import java.net.InetAddress
import akka.http.scaladsl.model.RemoteAddress.IP

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
      domainFqn,
      protoConfig,
      IP(ip=InetAddress.getLocalHost),
      "")

    val clientActor = system.actorOf(props)

    clientActor ! WebSocketOpened(connectionActor.ref)
  }

  class HandshookClient(system: ActorSystem) extends TestFixture(system: ActorSystem) {
    val domainActor = new TestProbe(system)
    val modelManagerActor = new TestProbe(system)
    val modelStoreActor = new TestProbe(system)
    val operationStoreActor = new TestProbe(system)
    val userServiceActor = new TestProbe(system)
    val presenceServiceActor = new TestProbe(system)
    val chatLookupActor = new TestProbe(system)
    val chatChannelActor = new TestProbe(system)

    val handshakeRequestMessage = HandshakeRequestMessage(false, None)
    val handshakeCallback = new TestReplyCallback()
    val handshakeEvent = RequestReceived(handshakeRequestMessage, handshakeCallback)

    clientActor.tell(handshakeEvent, ActorRef.noSender)

    domainManagerActor.expectMsgClass(FiniteDuration(1, TimeUnit.SECONDS), classOf[HandshakeRequest])
    domainManagerActor.reply(
        HandshakeSuccess(modelManagerActor.ref, modelStoreActor.ref, operationStoreActor.ref, userServiceActor.ref, presenceServiceActor.ref, chatLookupActor.ref))
    Await.result(handshakeCallback.result, 250 millis)
  }

  class AuthenticatedClient(system: ActorSystem) extends HandshookClient(system: ActorSystem) {
    val authRequestMessage = PasswordAuthRequestMessage("testuser", "testpass")

    val authCallback = new TestReplyCallback()
    val authEvent = RequestReceived(authRequestMessage, authCallback)

    clientActor.tell(authEvent, ActorRef.noSender)

    domainActor.expectMsgClass(FiniteDuration(1, TimeUnit.SECONDS), classOf[PasswordAuthRequest])
    domainActor.reply(AuthenticationSuccess("test", SessionKey("test", "0"), "123"))

    val AuthenticationResponseMessage(ok, Some(username), Some(session), Some(RecconnectToken), Some(state)) = Await.result(authCallback.result, 250 millis)
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

    def expectedError(code: String, message: String, details: Map[String, Any]): Unit = {
      p.failure(new IllegalStateException())
    }
    
    def expectedError(code: String, message: String): Unit = {
      p.failure(new IllegalStateException())
    }
  }
}
