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
import com.convergencelabs.convergence.server.backend.services.domain.DomainSessionActor.{ConnectionRequest, DomainNotFound, DomainUnavailable, Message}
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
    "receiving an initial connection request" must {
      "respond with a handshake success if the domain is online" in new TestFixture {
        Mockito.when(provider.domainStateProvider.getDomainState())
          .thenReturn(Success(Some(DomainState(domainId, DomainAvailability.Online, DomainStatus.Ready))))
        assert(connect(domainId, domainActor).response.isRight)
      }

      "respond with a handshake error if the domain is offline" in new TestFixture {
        Mockito.when(provider.domainStateProvider.getDomainState())
          .thenReturn(Success(Some(DomainState(domainId, DomainAvailability.Offline, DomainStatus.Ready))))
        connect(domainId, domainActor).response shouldBe Left(DomainNotFound(domainId))
      }

      "respond with a handshake error if the domain status can't be found" in new TestFixture {
        Mockito.when(provider.domainStateProvider.getDomainState()).thenReturn(Success(None))
        connect(domainId, domainActor).response shouldBe Left(DomainNotFound(domainId))
      }

      "respond with a handshake success if the domain is in maintenance mode" in new TestFixture {
        Mockito.when(provider.domainStateProvider.getDomainState())
          .thenReturn(Success(Some(DomainState(domainId, DomainAvailability.Maintenance, DomainStatus.Ready))))
        connect(domainId, domainActor).response.isRight shouldBe true
      }

      "respond with a handshake error if the domain is in error status" in new TestFixture {
        Mockito.when(provider.domainStateProvider.getDomainState())
          .thenReturn(Success(Some(DomainState(domainId, DomainAvailability.Online, DomainStatus.Error))))
        connect(domainId, domainActor).response shouldBe Left(DomainUnavailable(domainId))
      }

      "respond with a handshake error if the domain is in initializing" in new TestFixture {
        Mockito.when(provider.domainStateProvider.getDomainState())
          .thenReturn(Success(Some(DomainState(domainId, DomainAvailability.Online, DomainStatus.Initializing))))
        connect(domainId, domainActor).response shouldBe Left(DomainUnavailable(domainId))
      }
    }
  }

  private[this] def connect(domainId: DomainId, domainActor: ActorRef[DomainSessionActor.Message]): DomainSessionActor.ConnectionResponse = {
    val client: TestProbe[ClientActor.Disconnect] = testKit.createTestProbe[ClientActor.Disconnect]()
    val replyTo: TestProbe[DomainSessionActor.ConnectionResponse] = testKit.createTestProbe[DomainSessionActor.ConnectionResponse]()
    val request = ConnectionRequest(
      domainId,
      client.ref,
      "127.0.0.1",
      "javascript",
      "1.0",
      "some ua",
      AnonymousAuthRequest(None),
      replyTo.ref)

    domainActor ! request
    replyTo.expectMessageType[DomainSessionActor.ConnectionResponse](FiniteDuration(1, TimeUnit.SECONDS))
  }

  trait TestFixture {
    val domainId: DomainId = DomainId("convergence", "default")

    val provider = new MockDomainPersistenceProvider(domainId)
    val persistenceManager = new MockDomainPersistenceManager(Map(domainId -> provider))

    val shardRegion: TestProbe[Message] = testKit.createTestProbe[Message]()
    val shard: TestProbe[ClusterSharding.ShardCommand] = testKit.createTestProbe[ClusterSharding.ShardCommand]()
    val domainLifecycleTopic: TestProbe[DomainLifecycleTopic.TopicMessage] =
      testKit.createTestProbe[DomainLifecycleTopic.TopicMessage]()

    private val behavior: Behavior[DomainSessionActor.Message] = DomainSessionActor(
      domainId,
      shardRegion.ref,
      shard.ref,
      persistenceManager,
      FiniteDuration(10, TimeUnit.SECONDS),
      domainLifecycleTopic.ref)

    val domainActor: ActorRef[Message] = testKit.spawn(behavior)
  }
}
