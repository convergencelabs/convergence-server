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

package com.convergencelabs.convergence.server.domain

import java.util.concurrent.TimeUnit

import akka.actor.testkit.typed.scaladsl.{ScalaTestWithActorTestKit, TestProbe}
import akka.actor.typed.{ActorRef, Behavior}
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import com.convergencelabs.convergence.server.api.realtime.ClientActor
import com.convergencelabs.convergence.server.db.provision.DomainLifecycleTopic
import com.convergencelabs.convergence.server.domain.DomainActor.Message
import com.convergencelabs.convergence.server.util.{MockDomainPersistenceManager, MockDomainPersistenceProvider}
import com.convergencelabs.convergence.server.{HeartbeatConfiguration, ProtocolConfiguration}
import com.typesafe.config.ConfigFactory
import org.scalatest.BeforeAndAfterAll
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatestplus.mockito.MockitoSugar

import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.language.postfixOps

class DomainActorSpec
    extends ScalaTestWithActorTestKit(ConfigFactory.parseResources("cluster-application.conf"))
    with AnyWordSpecLike
    with BeforeAndAfterAll
    with MockitoSugar {

  override def afterAll(): Unit = testKit.shutdownTestKit()

  "A DomainActor" when {
    "receiving an initial handshake request" must {
      "response with a handshake success" in new TestFixture {
        val client = testKit.createTestProbe[ClientActor.Disconnect]()
        val replyTo = testKit.createTestProbe[DomainActor.HandshakeResponse]()
        domainActor ! DomainActor.HandshakeRequest(domainId, client.ref, reconnect = false, None, replyTo.ref)
        val response: DomainActor.HandshakeResponse =
          replyTo.expectMessageType[DomainActor.HandshakeResponse](FiniteDuration(1, TimeUnit.SECONDS))
        assert(response.handshake.isRight)
      }
    }
  }

  trait TestFixture {
    val domainId: DomainId = DomainId("convergence", "default")

    val provider = new MockDomainPersistenceProvider(domainId)
    val persistenceManager = new MockDomainPersistenceManager(Map(domainId -> provider))

    val protocolConfig: ProtocolConfiguration = ProtocolConfiguration(
      2 seconds,
      2 seconds,
      HeartbeatConfiguration(
        enabled = false,
        0 seconds,
        0 seconds))

    val shardRegion: TestProbe[Message] = testKit.createTestProbe[Message]()
    val shard: TestProbe[ClusterSharding.ShardCommand] = testKit.createTestProbe[ClusterSharding.ShardCommand]()
    val domainLifecycleTopic: TestProbe[DomainLifecycleTopic.TopicMessage] =
      testKit.createTestProbe[DomainLifecycleTopic.TopicMessage]()


    private val behavior: Behavior[DomainActor.Message] = DomainActor(
      shardRegion.ref,
      shard.ref,
      protocolConfig,
      persistenceManager,
      FiniteDuration(10, TimeUnit.SECONDS),
      domainLifecycleTopic.ref)

    val domainActor: ActorRef[Message] = testKit.spawn(behavior)
  }
}
