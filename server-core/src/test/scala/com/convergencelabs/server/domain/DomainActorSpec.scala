package com.convergencelabs.server.domain

import java.util.concurrent.TimeUnit

import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.FiniteDuration
import scala.language.postfixOps

import org.junit.runner.RunWith
import org.mockito.Mockito
import org.scalatest.BeforeAndAfterAll
import org.scalatest.Finders
import org.scalatest.WordSpecLike
import org.scalatest.junit.JUnitRunner
import org.scalatest.mock.MockitoSugar

import com.convergencelabs.server.HeartbeatConfiguration
import com.convergencelabs.server.ProtocolConfiguration
import com.convergencelabs.server.datastore.domain.DomainPersistenceProvider
import com.convergencelabs.server.util.MockDomainPersistenceManager

import akka.actor.ActorSystem
import akka.testkit.TestKit
import akka.testkit.TestProbe
import scala.util.Success
import com.typesafe.config.ConfigFactory
import com.convergencelabs.server.util.MockDomainPersistenceProvider
import akka.cluster.sharding.ShardRegion.Passivate

@RunWith(classOf[JUnitRunner])
class DomainActorSpec
    extends TestKit(ActorSystem("DomainActorSpec", ConfigFactory.parseResources("cluster-application.conf")))
    with WordSpecLike
    with BeforeAndAfterAll
    with MockitoSugar {

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  "A DomainActor" when {
    "receiving an initial handshake request" must {
      "response with a handshake success" in new TestFixture {
        val client = new TestProbe(system)
        domainActor.tell(HandshakeRequest(domainFqn, client.ref, false, None), client.ref)
        val response = client.expectMsgClass(FiniteDuration(1, TimeUnit.SECONDS), classOf[HandshakeSuccess])
        assert(domainActor == response.domainActor)
      }
    }

    "receiving a client disconnect" must {
      "send a domain shutdown request when the last client disconnects" in new TestFixture {
        val client = new TestProbe(system)
        domainActor.tell(HandshakeRequest(domainFqn, client.ref, false, None), client.ref)
        val response = client.expectMsgClass(FiniteDuration(1, TimeUnit.SECONDS), classOf[HandshakeSuccess])

        domainActor.tell(ClientDisconnected(domainFqn, "sessionId"), client.ref)
        var request = parent.expectMsgClass(FiniteDuration(1, TimeUnit.SECONDS), classOf[Passivate])
      }
    }
  }

  trait TestFixture {
    val domainFqn = DomainFqn("convergence", "default")

    val provider = new MockDomainPersistenceProvider()
    val persistenceManager = new MockDomainPersistenceManager(Map(domainFqn -> provider))

    val protocolConfig = ProtocolConfiguration(
      2 seconds,
      2 seconds,
      HeartbeatConfiguration(
        false,
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
