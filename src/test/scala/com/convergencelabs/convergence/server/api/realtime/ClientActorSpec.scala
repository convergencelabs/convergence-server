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

import akka.actor.testkit.typed.scaladsl.{ScalaTestWithActorTestKit, TestProbe}
import akka.actor.typed.ActorRef
import akka.http.scaladsl.model.RemoteAddress.IP
import com.convergencelabs.convergence.proto.core.ConnectionRequestMessage.PasswordAuthRequestData
import com.convergencelabs.convergence.proto.core._
import com.convergencelabs.convergence.server.backend.services.domain._
import com.convergencelabs.convergence.server.backend.services.domain.activity.ActivityActor
import com.convergencelabs.convergence.server.backend.services.domain.chat.{ChatActor, ChatDeliveryActor, ChatServiceActor}
import com.convergencelabs.convergence.server.backend.services.domain.identity.IdentityServiceActor
import com.convergencelabs.convergence.server.backend.services.domain.model.{ModelOperationServiceActor, ModelServiceActor, RealtimeModelActor}
import com.convergencelabs.convergence.server.backend.services.domain.presence.PresenceServiceActor
import com.convergencelabs.convergence.server.backend.services.server.DomainLifecycleTopic
import com.convergencelabs.convergence.server.model.DomainId
import com.convergencelabs.convergence.server.model.domain.session.DomainSessionAndUserId
import com.convergencelabs.convergence.server.model.domain.user.{DomainUserId, DomainUserType}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatestplus.mockito.MockitoSugar

import java.net.InetAddress
import java.util.concurrent.TimeUnit
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
    val connectionActor: TestProbe[WebSocketService.WebSocketMessage] =
      testKit.createTestProbe[WebSocketService.WebSocketMessage]()

    val domainId: DomainId = DomainId("namespace", "domainId")
    val protoConfig: ProtocolConfiguration = ProtocolConfiguration(
      2 seconds,
      250 millis,
      ProtocolConfiguration.HeartbeatConfiguration(
        enabled = false,
        0 seconds,
        0 seconds))

    val domainRegion: TestProbe[DomainActor.Message] = testKit.createTestProbe[DomainActor.Message]()
    val activityShardRegion: TestProbe[ActivityActor.Message] = testKit.createTestProbe[ActivityActor.Message]()
    val modelShardRegion: TestProbe[RealtimeModelActor.Message] = testKit.createTestProbe[RealtimeModelActor.Message]()
    val chatShardRegion: TestProbe[ChatActor.Message] = testKit.createTestProbe[ChatActor.Message]()
    val chatDeliveryShardRegion: TestProbe[ChatDeliveryActor.Message] = testKit.createTestProbe[ChatDeliveryActor.Message]()

    val modelService: TestProbe[ModelServiceActor.Message] = testKit.createTestProbe[ModelServiceActor.Message]()
    val modelOperationService: TestProbe[ModelOperationServiceActor.Message] = testKit.createTestProbe[ModelOperationServiceActor.Message]()
    val identityService: TestProbe[IdentityServiceActor.Message] = testKit.createTestProbe[IdentityServiceActor.Message]()
    val chatService: TestProbe[ChatServiceActor.Message] = testKit.createTestProbe[ChatServiceActor.Message]()
    val presenceService: TestProbe[PresenceServiceActor.Message] = testKit.createTestProbe[PresenceServiceActor.Message]()

    val domainLifecycleTopic: TestProbe[DomainLifecycleTopic.TopicMessage] = testKit.createTestProbe[DomainLifecycleTopic.TopicMessage]()
    val modelSyncInterval: FiniteDuration = FiniteDuration(10, TimeUnit.SECONDS)

    val clientActor: ActorRef[ClientActor.Message] = testKit.spawn(ClientActor(
      domainId,
      protoConfig,
      IP(ip = InetAddress.getLocalHost),
      "test ua",
      domainRegion.ref,
      modelService.ref,
      modelOperationService.ref,
      chatService.ref,
      identityService.ref,
      presenceService.ref,
      activityShardRegion.ref,
      modelShardRegion.ref,
      chatShardRegion.ref,
      chatDeliveryShardRegion.ref,
      domainLifecycleTopic.ref,
      modelSyncInterval))

    def open(): Unit = {
      clientActor ! ClientActor.WebSocketOpened(connectionActor.ref)
    }

    def connect(): Unit = {
      val authRequestMessage = ConnectionRequestMessage()
        .withPassword(PasswordAuthRequestData("testuser", "testpass"))

      val authCallback = new TestReplyCallback()
      val authEvent = ClientActor.IncomingProtocolRequest(authRequestMessage, authCallback)

      clientActor ! authEvent

      val authRequest = domainRegion.expectMessageType[DomainActor.ConnectionRequest](FiniteDuration(1, TimeUnit.SECONDS))

      val success = DomainActor.ConnectionSuccess(DomainSessionAndUserId("0", DomainUserId(DomainUserType.Normal, "test")), Some("123"))
      authRequest.replyTo ! DomainActor.ConnectionResponse(Right(success))

      val authResponse = Await.result(authCallback.result, 250 millis).asInstanceOf[ConnectionResponseMessage]
      authResponse.response.isSuccess shouldBe true
    }
  }
}
