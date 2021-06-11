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

package com.convergencelabs.convergence.server.backend.services.domain

import akka.actor.testkit.typed.scaladsl.{ScalaTestWithActorTestKit, TestProbe}
import akka.actor.typed.{ActorRef, Behavior}
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import com.convergencelabs.convergence.server.api.realtime.ClientActor
import com.convergencelabs.convergence.server.backend.services.domain.DomainActor.{DomainNotFound, DomainUnavailable, HandshakeResponse, Message}
import com.convergencelabs.convergence.server.backend.services.server.DomainLifecycleTopic
import com.convergencelabs.convergence.server.model.DomainId
import com.convergencelabs.convergence.server.model.server.domain.{DomainAvailability, DomainState, DomainStatus}
import com.convergencelabs.convergence.server.util.{MockDomainPersistenceManager, MockDomainPersistenceProvider}
import com.typesafe.config.ConfigFactory
import org.mockito.Mockito
import org.scalatest.BeforeAndAfterAll
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatestplus.mockito.MockitoSugar

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration
import scala.language.postfixOps
import scala.util.Success

class DomainActorSpec
  extends ScalaTestWithActorTestKit(ConfigFactory.parseResources("cluster-application.conf"))
    with AnyWordSpecLike
    with BeforeAndAfterAll
    with MockitoSugar {

  override def afterAll(): Unit = testKit.shutdownTestKit()

  "A DomainActor" when {
    "receiving an initial handshake request" must {
      "respond with a handshake success if the domain is online" in new TestFixture {
        Mockito.when(provider.domainStateProvider.getDomainState())
          .thenReturn(Success(Some(DomainState(domainId, DomainAvailability.Online, DomainStatus.Ready))))
        assert(handshake(domainId, domainActor).handshake.isRight)
      }

      "respond with a handshake error if the domain is offline" in new TestFixture {
        Mockito.when(provider.domainStateProvider.getDomainState())
          .thenReturn(Success(Some(DomainState(domainId, DomainAvailability.Offline, DomainStatus.Ready))))
        handshake(domainId, domainActor).handshake shouldBe Left(DomainNotFound(domainId))
      }

      "respond with a handshake error if the domain status can't be found" in new TestFixture {
        Mockito.when(provider.domainStateProvider.getDomainState()).thenReturn(Success(None))
        handshake(domainId, domainActor).handshake shouldBe Left(DomainNotFound(domainId))
      }

      "respond with a handshake success if the domain is in maintenance mode" in new TestFixture {
        Mockito.when(provider.domainStateProvider.getDomainState())
          .thenReturn(Success(Some(DomainState(domainId, DomainAvailability.Maintenance, DomainStatus.Ready))))
        handshake(domainId, domainActor).handshake.isRight shouldBe true
      }

      "respond with a handshake error if the domain is in error status" in new TestFixture {
        Mockito.when(provider.domainStateProvider.getDomainState())
          .thenReturn(Success(Some(DomainState(domainId, DomainAvailability.Online, DomainStatus.Error))))
        handshake(domainId, domainActor).handshake shouldBe Left(DomainUnavailable(domainId))
      }

      "respond with a handshake error if the domain is in initializing" in new TestFixture {
        Mockito.when(provider.domainStateProvider.getDomainState())
          .thenReturn(Success(Some(DomainState(domainId, DomainAvailability.Online, DomainStatus.Initializing))))
        handshake(domainId, domainActor).handshake shouldBe Left(DomainUnavailable(domainId))
      }
    }
  }

  private[this] def handshake(domainId: DomainId, domainActor: ActorRef[DomainActor.Message]): HandshakeResponse = {
    val client: TestProbe[ClientActor.Disconnect] = testKit.createTestProbe[ClientActor.Disconnect]()
    val replyTo: TestProbe[DomainActor.HandshakeResponse] = testKit.createTestProbe[DomainActor.HandshakeResponse]()
    domainActor ! DomainActor.HandshakeRequest(domainId, client.ref, reconnect = false, None, replyTo.ref)
    replyTo.expectMessageType[DomainActor.HandshakeResponse](FiniteDuration(1, TimeUnit.SECONDS))
  }

  trait TestFixture {
    val domainId: DomainId = DomainId("convergence", "default")

    val provider = new MockDomainPersistenceProvider(domainId)
    val persistenceManager = new MockDomainPersistenceManager(Map(domainId -> provider))

    val shardRegion: TestProbe[Message] = testKit.createTestProbe[Message]()
    val shard: TestProbe[ClusterSharding.ShardCommand] = testKit.createTestProbe[ClusterSharding.ShardCommand]()
    val domainLifecycleTopic: TestProbe[DomainLifecycleTopic.TopicMessage] =
      testKit.createTestProbe[DomainLifecycleTopic.TopicMessage]()

    private val behavior: Behavior[DomainActor.Message] = DomainActor(
      domainId,
      shardRegion.ref,
      shard.ref,
      persistenceManager,
      FiniteDuration(10, TimeUnit.SECONDS),
      domainLifecycleTopic.ref)

    val domainActor: ActorRef[Message] = testKit.spawn(behavior)
  }
}
