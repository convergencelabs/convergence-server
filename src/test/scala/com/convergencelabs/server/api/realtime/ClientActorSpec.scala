package com.convergencelabs.server.api.realtime

import java.net.InetAddress
import java.util.concurrent.TimeUnit

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.model.RemoteAddress.IP
import akka.testkit.{TestKit, TestProbe}
import com.convergencelabs.convergence.proto.core.AuthenticationRequestMessage.PasswordAuthRequestData
import com.convergencelabs.convergence.proto.core.AuthenticationResponseMessage.AuthSuccessData
import com.convergencelabs.server.{HeartbeatConfiguration, ProtocolConfiguration}
import com.convergencelabs.server.domain._
import com.convergencelabs.convergence.proto._
import com.convergencelabs.convergence.proto.core._
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import org.scalatest.mockito.MockitoSugar
import scalapb.GeneratedMessage

import scala.concurrent.{Await, Future, Promise}
import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.language.postfixOps


// scalastyle:off magic.number
class ClientActorSpec
  extends TestKit(ActorSystem("ClientActorSpec"))
  with WordSpecLike
  with BeforeAndAfterAll
  with MockitoSugar 
  with Matchers {

  val SessionId = "sessionId"
  val ReconnectToken = "ReconnectToken"

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  "A ClientActor" when {

  }

  class TestFixture(system: ActorSystem) {
    val domainManagerActor = new TestProbe(system)

    val connectionActor = new TestProbe(system)

    val domainFqn = DomainId("namespace", "domainId")
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
      IP(ip = InetAddress.getLocalHost),
      "")

    val clientActor = system.actorOf(props)

    clientActor ! WebSocketOpened(connectionActor.ref)
  }

  class HandshookClient(system: ActorSystem) extends TestFixture(system: ActorSystem) {
    val domainActor = new TestProbe(system)
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
      HandshakeSuccess(modelStoreActor.ref, operationStoreActor.ref, userServiceActor.ref, presenceServiceActor.ref, chatLookupActor.ref))
    Await.result(handshakeCallback.result, 250 millis)
  }

  class AuthenticatedClient(system: ActorSystem) extends HandshookClient(system: ActorSystem) {
    val authRequestMessage = AuthenticationRequestMessage()
      .withPassword(PasswordAuthRequestData("testuser", "testpass"))

    val authCallback = new TestReplyCallback()
    val authEvent = RequestReceived(authRequestMessage, authCallback)

    clientActor.tell(authEvent, ActorRef.noSender)

    domainActor.expectMsgClass(FiniteDuration(1, TimeUnit.SECONDS), classOf[PasswordAuthRequest])
    domainActor.reply(AuthenticationSuccess(DomainUserSessionId("0", DomainUserId(DomainUserType.Normal, "test")), Some("123")))

    val authResponse = Await.result(authCallback.result, 250 millis).asInstanceOf[AuthenticationResponseMessage]
    authResponse.response.isSuccess shouldBe true
    val AuthSuccessData(username, sessionId, reconnectToken, presenceState) = authResponse.getSuccess
  }

  class TestReplyCallback() extends ReplyCallback {
    val p = Promise[GeneratedMessage with ResponseMessage]

    def result(): Future[GeneratedMessage with ResponseMessage] = {
      p.future
    }

    def reply(message: GeneratedMessage with ResponseMessage): Unit = {
      p.success(message)
    }

    def unknownError(): Unit = {
      p.failure(new IllegalStateException())
    }

    def unexpectedError(details: String): Unit = {
      p.failure(new IllegalStateException())
    }

    def expectedError(code: String, message: String, details: Map[String, String]): Unit = {
      p.failure(new IllegalStateException())
    }

    def expectedError(code: String, message: String): Unit = {
      p.failure(new IllegalStateException())
    }
  }
}
