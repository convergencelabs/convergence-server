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

import akka.actor.ActorSystem
import akka.testkit.{TestKit, TestProbe}
import com.convergencelabs.convergence.server.util.{MockDomainPersistenceManager, MockDomainPersistenceProvider}
import com.convergencelabs.convergence.server.{HeartbeatConfiguration, ProtocolConfiguration}
import com.typesafe.config.ConfigFactory
import org.scalatest.BeforeAndAfterAll
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatestplus.mockito.MockitoSugar

import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.language.postfixOps

class DomainActorSpec
    extends TestKit(ActorSystem("DomainActorSpec", ConfigFactory.parseResources("cluster-application.conf")))
    with AnyWordSpecLike
    with BeforeAndAfterAll
    with MockitoSugar {

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  "A DomainActor" when {
    "receiving an initial handshake request" must {
      "response with a handshake success" in new TestFixture {
        val client = new TestProbe(system)
        domainActor.tell(HandshakeRequest(domainFqn, client.ref, reconnect = false, None), client.ref)
        val response: HandshakeSuccess = client.expectMsgClass(FiniteDuration(1, TimeUnit.SECONDS), classOf[HandshakeSuccess])
      }
    }
  }

  trait TestFixture {
    val domainFqn = DomainId("convergence", "default")

    val provider = new MockDomainPersistenceProvider()
    val persistenceManager = new MockDomainPersistenceManager(Map(domainFqn -> provider))

    val protocolConfig = ProtocolConfiguration(
      2 seconds,
      2 seconds,
      HeartbeatConfiguration(
        enabled = false,
        0 seconds,
        0 seconds))

    val parent = new TestProbe(system)

    val props = DomainActor.props(
      protocolConfig,
      persistenceManager,
      FiniteDuration(10, TimeUnit.SECONDS))

    val domainActor = parent.childActorOf(props)
  }
}
