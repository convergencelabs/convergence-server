package com.convergencelabs.server.domain

import java.util.concurrent.TimeUnit

import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.FiniteDuration
import scala.language.postfixOps
import scala.util.Success

import org.junit.runner.RunWith
import org.mockito.Mockito
import org.scalatest.BeforeAndAfterAll
import org.scalatest.WordSpecLike
import org.scalatest.junit.JUnitRunner
import org.scalatest.mock.MockitoSugar

import com.convergencelabs.server.HeartbeatConfiguration
import com.convergencelabs.server.ProtocolConfiguration
import com.convergencelabs.server.datastore.DomainStore
import com.convergencelabs.server.datastore.domain.DomainPersistenceProvider
import com.convergencelabs.server.util.MockDomainPersistenceManager
import com.typesafe.config.ConfigFactory

import akka.actor.ActorSystem
import akka.testkit.TestKit
import akka.testkit.TestProbe
import com.convergencelabs.server.datastore.domain.DomainPersistenceManager
import com.convergencelabs.server.util.MockDomainPersistenceManager
import com.convergencelabs.server.util.MockDomainPersistenceProvider

class DomainManagerActorSpec()
    extends TestKit(ActorSystem("DomainManagerActorSpec", ConfigFactory.parseResources("cluster-application.conf")))
    with WordSpecLike
    with BeforeAndAfterAll
    with MockitoSugar {


  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  "A DomainManagerActor" when {
    "receiving a handshake request" must {
      "respond with a handshake success for a domain that exists" in new TestFixture {
        val client = new TestProbe(system)
        domainManagerActor.tell(HandshakeRequest(domainFqn, client.ref, false, None), client.ref)
        val response = client.expectMsgClass(FiniteDuration(1, TimeUnit.SECONDS), classOf[HandshakeSuccess])
      }

      "respond with a handshake failure for a domain that doesn't exist" in new TestFixture {
        val client = new TestProbe(system)
        domainManagerActor.tell(HandshakeRequest(nonExistingDomain, client.ref, false, None), client.ref)
        val response = client.expectMsgClass(FiniteDuration(1, TimeUnit.SECONDS), classOf[HandshakeFailure])
      }
    }
  }

  trait TestFixture {
    val domainFqn = DomainFqn("namespace1", "domain1")
    val nonExistingDomain = DomainFqn("no", "domain")

    val keys = Map[String, JwtAuthKey]()
    val adminKeyPair = JwtKeyPair("", "")

    val domain = Domain(
      domainFqn,
      "Default",
      "test",
      DomainStatus.Online,
      "")

    val domainStore = mock[DomainStore]
    Mockito.when(domainStore.getDomainByFqn(domainFqn)).thenReturn(Success(Some(domain)))
    Mockito.when(domainStore.getDomainByFqn(nonExistingDomain)).thenReturn(Success(None))
    Mockito.when(domainStore.domainExists(domainFqn)).thenReturn(Success(true))
    Mockito.when(domainStore.domainExists(nonExistingDomain)).thenReturn(Success(false))

    val provider = new MockDomainPersistenceProvider()
    val persistenceManager = new MockDomainPersistenceManager(Map(domainFqn -> provider))

    val protocolConfig = ProtocolConfiguration(
      2 seconds,
      2 seconds,
      HeartbeatConfiguration(
        false,
        0 seconds,
        0 seconds))

    val domainManagerActor = system.actorOf(
      DomainManagerActor.props(domainStore, protocolConfig, persistenceManager))
  }
}
