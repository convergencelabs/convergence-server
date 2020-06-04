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

import java.net.InetAddress
import java.util.concurrent.TimeUnit

import akka.actor.testkit.typed.scaladsl.{ScalaTestWithActorTestKit, TestProbe}
import akka.actor.typed.ActorRef
import akka.http.scaladsl.model.RemoteAddress.IP
import com.convergencelabs.convergence.proto.core.AuthenticationRequestMessage.PasswordAuthRequestData
import com.convergencelabs.convergence.proto.core._
import com.convergencelabs.convergence.server.datastore.domain.{ModelOperationStoreActor, ModelStoreActor}
import com.convergencelabs.convergence.server.db.provision.DomainLifecycleTopic
import com.convergencelabs.convergence.server.domain._
import com.convergencelabs.convergence.server.domain.activity.ActivityActor
import com.convergencelabs.convergence.server.domain.chat.{ChatActor, ChatManagerActor}
import com.convergencelabs.convergence.server.domain.model.RealtimeModelActor
import com.convergencelabs.convergence.server.domain.presence.PresenceServiceActor
import com.convergencelabs.convergence.server.{HeartbeatConfiguration, ProtocolConfiguration}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatestplus.mockito.MockitoSugar

import scala.concurrent.Await
import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.language.postfixOps


// scalastyle:off magic.number
class ClientActorSpec
  extends ScalaTestWithActorTestKit
    with AnyWordSpecLike
    with BeforeAndAfterAll
    with MockitoSugar
    with Matchers {

  val SessionId = "sessionId"
  val ReconnectToken = "ReconnectToken"

  override def afterAll(): Unit = testKit.shutdownTestKit()

  "A ClientActor" when {

  }

  class TestFixture() {
    val connectionActor: TestProbe[ConnectionActor.ClientMessage] =
      testKit.createTestProbe[ConnectionActor.ClientMessage]()

    val domainId: DomainId = DomainId("namespace", "domainId")
    val protoConfig: ProtocolConfiguration = ProtocolConfiguration(
      2 seconds,
      250 millis,
      HeartbeatConfiguration(
        enabled = false,
        0 seconds,
        0 seconds))

    val domainRegion: TestProbe[DomainActor.Message] = testKit.createTestProbe[DomainActor.Message]()
    val activityShardRegion: TestProbe[ActivityActor.Message] = testKit.createTestProbe[ActivityActor.Message]()
    val modelShardRegion: TestProbe[RealtimeModelActor.Message] = testKit.createTestProbe[RealtimeModelActor.Message]()
    val chatShardRegion: TestProbe[ChatActor.Message] = testKit.createTestProbe[ChatActor.Message]()
    val domainLifecycleTopic: TestProbe[DomainLifecycleTopic.TopicMessage] = testKit.createTestProbe[DomainLifecycleTopic.TopicMessage]()
    val modelSyncInterval: FiniteDuration = FiniteDuration(10, TimeUnit.SECONDS)

    val clientActor: ActorRef[ClientActor.Message] = testKit.spawn(ClientActor(
      domainId,
      protoConfig,
      IP(ip = InetAddress.getLocalHost),
      "test ua",
      domainRegion.ref,
      activityShardRegion.ref,
      modelShardRegion.ref,
      chatShardRegion.ref,
      domainLifecycleTopic.ref,
      modelSyncInterval))

    clientActor ! ClientActor.ConnectionOpened(connectionActor.ref)
  }

  class HandshookClient() extends TestFixture() {
    val domainActor: TestProbe[DomainActor.Message] = testKit.createTestProbe[DomainActor.Message]()

    val modelStoreActor: TestProbe[ModelStoreActor.Message] = testKit.createTestProbe[ModelStoreActor.Message]()
    val operationStoreActor: TestProbe[ModelOperationStoreActor.Message] = testKit.createTestProbe[ModelOperationStoreActor.Message]()
    val userServiceActor: TestProbe[IdentityServiceActor.Message] = testKit.createTestProbe[IdentityServiceActor.Message]()
    val presenceServiceActor: TestProbe[PresenceServiceActor.Message] = testKit.createTestProbe[PresenceServiceActor.Message]()
    val chatManagerActor: TestProbe[ChatManagerActor.Message] = testKit.createTestProbe[ChatManagerActor.Message]()

    {
      val handshakeRequestMessage = HandshakeRequestMessage(reconnect = false, None)
      val handshakeCallback = new TestReplyCallback()
      val handshakeEvent = ClientActor.IncomingProtocolRequest(handshakeRequestMessage, handshakeCallback)

      clientActor ! handshakeEvent

      val message = domainActor.expectMessageType[DomainActor.HandshakeRequest](FiniteDuration(1, TimeUnit.SECONDS))

      val success = DomainActor.HandshakeSuccess(modelStoreActor.ref, operationStoreActor.ref, userServiceActor.ref, presenceServiceActor.ref, chatManagerActor.ref)
      message.replyTo ! DomainActor.HandshakeResponse(Right(success))
      Await.result(handshakeCallback.result, 250 millis)
    }
  }

  class AuthenticatedClient() extends HandshookClient() {
    {
      val authRequestMessage = AuthenticationRequestMessage()
        .withPassword(PasswordAuthRequestData("testuser", "testpass"))

      val authCallback = new TestReplyCallback()
      val authEvent = ClientActor.IncomingProtocolRequest(authRequestMessage, authCallback)

      clientActor ! authEvent

      val authRequest = domainActor.expectMessageType[DomainActor.AuthenticationRequest](FiniteDuration(1, TimeUnit.SECONDS))

      val reply = DomainActor.AuthenticationSuccess(DomainUserSessionId("0", DomainUserId(DomainUserType.Normal, "test")), Some("123"))
      authRequest.replyTo ! reply

      val authResponse = Await.result(authCallback.result, 250 millis).asInstanceOf[AuthenticationResponseMessage]
      authResponse.response.isSuccess shouldBe true
    }
  }
}
