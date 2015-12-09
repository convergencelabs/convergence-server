package com.convergencelabs.server.domain

import java.util.concurrent.TimeUnit

import scala.concurrent.duration.FiniteDuration
import scala.util.Success

import org.junit.runner.RunWith
import org.mockito.Mockito
import org.scalatest.BeforeAndAfterAll
import org.scalatest.Finders
import org.scalatest.WordSpecLike
import org.scalatest.junit.JUnitRunner
import org.scalatest.mock.MockitoSugar

import com.convergencelabs.server.ProtocolConfiguration
import com.convergencelabs.server.datastore.DomainStore
import com.convergencelabs.server.datastore.PersistenceProvider
import com.convergencelabs.server.datastore.domain.DomainPersistenceProvider
import com.convergencelabs.server.util.MockDomainPersistenceManagerActor
import com.typesafe.config.ConfigFactory

import akka.actor.ActorSystem
import akka.testkit.TestKit
import akka.testkit.TestProbe

@RunWith(classOf[JUnitRunner])
class DomainManagerActorSpec()
    extends TestKit(ActorSystem("DomainManagerActorSpec", ConfigFactory.parseResources("cluster-application.conf")))
    with WordSpecLike
    with BeforeAndAfterAll
    with MockitoSugar {

  val domainPersistence = MockDomainPersistenceManagerActor(system)

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
    val domainFqn = DomainFqn("convergence", "default")
    val nonExistingDomain = DomainFqn("no", "domain")

    val keys = Map[String, TokenPublicKey]()
    val adminKeyPair = TokenKeyPair("", "")


    val domain = Domain(
      "d1",
      domainFqn,
      "Default",
      "",
      "")

    val domainStore = mock[DomainStore]
    Mockito.when(domainStore.getDomainByFqn(domainFqn)).thenReturn(Success(Some(domain)))
    Mockito.when(domainStore.domainExists(domainFqn)).thenReturn(Success(true))
    Mockito.when(domainStore.domainExists(nonExistingDomain)).thenReturn(Success(false))

    val provider = mock[DomainPersistenceProvider]
    Mockito.when(provider.validateConnection()).thenReturn(true)
    domainPersistence.underlyingActor.mockProviders = Map(domainFqn -> provider)

    val convergencePersistence = mock[PersistenceProvider]
    Mockito.when(convergencePersistence.domainStore).thenReturn(domainStore)

    val protocolConfig = ProtocolConfiguration(1000L)

    val domainManagerActor = system.actorOf(
      DomainManagerActor.props(convergencePersistence, protocolConfig))
  }
}
