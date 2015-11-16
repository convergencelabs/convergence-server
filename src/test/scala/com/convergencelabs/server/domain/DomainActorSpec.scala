package com.convergencelabs.server.domain

import java.util.concurrent.TimeUnit
import akka.actor.ActorSystem
import akka.testkit.TestKit
import akka.testkit.TestProbe
import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.FiniteDuration
import scala.language.postfixOps
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
import com.convergencelabs.server.datastore.domain.DomainPersistenceProvider
import com.convergencelabs.server.util.MockDomainPersistenceManagerActor
import com.convergencelabs.server.datastore.domain.DomainPersistenceProvider
import org.mockito.Mockito
import com.convergencelabs.server.domain.model.SnapshotConfig
import java.time.temporal.ChronoUnit
import java.time.Duration

@RunWith(classOf[JUnitRunner])
class DomainActorSpec
    extends TestKit(ActorSystem("DomainActorSpec"))
    with WordSpecLike
    with BeforeAndAfterAll
    with MockitoSugar {

  override def afterAll() {
    TestKit.shutdownActorSystem(system)
  }

  val domainPersistence = MockDomainPersistenceManagerActor(system)

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

        domainActor.tell(ClientDisconnected("sessionId"), client.ref)
        var request = domainManagerActor.expectMsgClass(FiniteDuration(1, TimeUnit.SECONDS), classOf[DomainShutdownRequest])
        assert(domainFqn == request.domainFqn)
      }
    }
  }

  trait TestFixture {
    val domainFqn = DomainFqn("convergence", "default")
    val keys = Map[String, TokenPublicKey]()
    val adminKeyPair = TokenKeyPair("", "")

    val snapshotConfig = SnapshotConfig(
      false,
      true,
      true,
      250,
      500,
      false,
      false,
      Duration.of(0, ChronoUnit.MINUTES),
      Duration.of(0, ChronoUnit.MINUTES))

    val domainConfig = DomainConfig(
      "d1",
      domainFqn,
      "Default",
      "",
      "",
      keys,
      adminKeyPair,
      snapshotConfig)

    val provider = mock[DomainPersistenceProvider]
    Mockito.when(provider.validateConnection()).thenReturn(true)
    domainPersistence.underlyingActor.mockProviders = Map(domainFqn -> provider)

    val domainManagerActor = new TestProbe(system)

    val protocolConfig = ProtocolConfiguration(1000L)

    val props = DomainActor.props(
      domainManagerActor.ref,
      domainConfig,
      protocolConfig,
      10 seconds)

    val domainActor = system.actorOf(props)
  }
}