package com.convergencelabs.server.frontend.realtime

import java.util.concurrent.TimeUnit
import akka.actor.ActorSystem
import akka.testkit.{ TestProbe, TestKit }
import org.json4s.JsonAST.{ JObject, JString }
import org.mockito.{ ArgumentCaptor, Mockito, Matchers }
import org.mockito.Mockito.{ verify, times }
import org.scalatest.mock.MockitoSugar
import org.scalatest.{ BeforeAndAfterAll, WordSpecLike }
import scala.concurrent.duration.FiniteDuration
import org.scalatest.junit.JUnitRunner
import org.junit.runner.RunWith
import com.convergencelabs.server.domain.DomainFqn
import com.convergencelabs.server.ProtocolConfiguration
import com.convergencelabs.server.frontend.realtime.proto.HandshakeRequestMessage
import com.convergencelabs.server.frontend.realtime.proto.OutgoingProtocolMessage
import scala.concurrent.Promise
import com.convergencelabs.server.frontend.realtime.proto.OutgoingProtocolResponseMessage
import akka.actor.ActorRef
import com.convergencelabs.server.domain.HandshakeRequest

@RunWith(classOf[JUnitRunner])
class ClientActorSpec(system: ActorSystem)
    extends TestKit(system)
    with WordSpecLike
    with BeforeAndAfterAll
    with MockitoSugar {

  def this() = this(ActorSystem("ClientActorSpec"))

  override def afterAll() {
    TestKit.shutdownActorSystem(system)
  }

  "A ClientActor" when {
    "opening a closed model" must {
      "load the model from the database if it is persisted" in new TestFixture(system) {

        val handshakeRequestMessage = HandshakeRequestMessage(false, None, None)
        val reply = Promise[OutgoingProtocolResponseMessage]
        val event = RequestReceived(handshakeRequestMessage, reply)
        
        clientActor.tell(event, ActorRef.noSender)
        
        domainManagerActor.expectMsgClass(FiniteDuration(1, TimeUnit.SECONDS), classOf[HandshakeRequest])
      }
    }
  }

  class TestFixture(system: ActorSystem) {
    val domainManagerActor = new TestProbe(system)
    val domainFqn = DomainFqn("namespace", "domainId")
    val protoConfig = ProtocolConfiguration(2L)

    val connection = mock[ProtocolConnection]

    val props = ClientActor.props(
      domainManagerActor.ref,
      connection,
      domainFqn
      )

    val clientActor = system.actorOf(props)
  }
}