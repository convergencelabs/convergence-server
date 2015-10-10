package com.convergencelabs.server.domain

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration
import org.json4s.JsonAST.JObject
import org.junit.runner.RunWith
import org.scalatest.BeforeAndAfterAll
import org.scalatest.WordSpecLike
import org.scalatest.junit.JUnitRunner
import org.scalatest.mock.MockitoSugar
import com.convergencelabs.server.ProtocolConfiguration
import com.convergencelabs.server.datastore.ConfigurationStore
import com.convergencelabs.server.datastore.DomainConfig
import com.convergencelabs.server.datastore.TokenKeyPair
import com.convergencelabs.server.datastore.TokenPublicKey
import akka.actor.ActorSystem
import akka.testkit.TestKit
import akka.testkit.TestProbe
import com.convergencelabs.server.domain.auth.InternalDomainAuthenticationProvider
import com.convergencelabs.server.datastore.domain.DomainPersistenceProvider
import com.convergencelabs.server.datastore.PersistenceProvider
import org.mockito.Mockito
import com.convergencelabs.server.datastore.DomainConfigurationStore
import com.convergencelabs.server.datastore.domain.DomainPersistenceProvider
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigParseOptions

@RunWith(classOf[JUnitRunner])
class DomainManagerActorSpec()
    extends TestKit(ActorSystem("DomainManagerActorSpec",ConfigFactory.parseResources("cluster-application.conf")))
    with WordSpecLike
    with BeforeAndAfterAll
    with MockitoSugar {

  override def afterAll() {
    TestKit.shutdownActorSystem(system)
  }

  "A DomainManagerActor" when {
    "receiving a handshake request" must {
      "respond with a handshake response" in new TestFixture {
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
    val domainConfig = DomainConfig(
      "d1",
      domainFqn,
      "Default",
      JObject(),
      keys,
      adminKeyPair)

    val configStore = mock[DomainConfigurationStore]
    Mockito.when(configStore.getDomainConfig(domainFqn)).thenReturn(domainConfig)
    Mockito.when(configStore.domainExists(domainFqn)).thenReturn(true)
    Mockito.when(configStore.domainExists(nonExistingDomain)).thenReturn(false)

    val domainPersistenceProvider = mock[DomainPersistenceProvider]

    val convergencePersistence = mock[PersistenceProvider]
    Mockito.when(convergencePersistence.domainConfigStore).thenReturn(configStore)
    Mockito.when(convergencePersistence.getDomainPersitenceProvider(domainFqn)).thenReturn(domainPersistenceProvider)

    val internalAuthProvider = mock[InternalDomainAuthenticationProvider]

    val protocolConfig = ProtocolConfiguration(1000L)

    val props = DomainManagerActor.props(
      convergencePersistence,
      internalAuthProvider,
      protocolConfig)

    val domainManagerActor = system.actorOf(props)
  }
}