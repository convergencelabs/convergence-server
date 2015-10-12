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


@RunWith(classOf[JUnitRunner])
class DomainActorSpec(system: ActorSystem)
    extends TestKit(system)
    with WordSpecLike
    with BeforeAndAfterAll
    with MockitoSugar {

  def this() = this(ActorSystem("DomainActorSpec"))

  override def afterAll() {
    TestKit.shutdownActorSystem(system)
  }


  "A DomainActor" when {
    "receiving an initial handshake request" must {
      "response with a handshake response" in new TestFixture {
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
        
        domainActor.tell(ClientDisconnected("sessionId", client.ref), client.ref)
        var request = domainManagerActor.expectMsgClass(FiniteDuration(1, TimeUnit.SECONDS), classOf[DomainShutdownRequest])
        assert(domainFqn == request.domainFqn)
      }
    }
  }

  trait TestFixture {
    val domainFqn = DomainFqn("convergence", "default")
    val keys = Map[String, TokenPublicKey]()
    val adminKeyPair = TokenKeyPair("", "")
    val domainConfig = DomainConfig(
      "d1",
      domainFqn,
      "Default",
      JObject(),
      keys,
      adminKeyPair)

    val domainPersistence = mock[DomainPersistenceProvider]
    val domainManagerActor = new TestProbe(system)
    val configStore = mock[ConfigurationStore]
    
    val protocolConfig = ProtocolConfiguration(1000L)
    
    val props = DomainActor.props(
      domainManagerActor.ref,
      domainConfig,
      domainPersistence,
      configStore,
      protocolConfig,
      10 seconds)
      
    val domainActor = system.actorOf(props)
  }
}