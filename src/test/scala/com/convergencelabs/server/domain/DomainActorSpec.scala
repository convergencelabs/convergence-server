package com.convergencelabs.server.domain

import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import akka.testkit.{TestKit, TestProbe}
import com.convergencelabs.server.{HeartbeatConfiguration, ProtocolConfiguration}
import com.convergencelabs.server.util.{MockDomainPersistenceManager, MockDomainPersistenceProvider}
import com.typesafe.config.ConfigFactory
import org.junit.runner.RunWith
import org.scalatest.{BeforeAndAfterAll, WordSpecLike}
import org.scalatest.junit.JUnitRunner
import org.scalatest.mockito.MockitoSugar

import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.language.postfixOps

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
