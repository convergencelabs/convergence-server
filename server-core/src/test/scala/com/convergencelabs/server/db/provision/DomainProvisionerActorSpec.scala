package com.convergencelabs.server.db.provision

import java.util.concurrent.TimeUnit

import scala.concurrent.duration.FiniteDuration
import scala.language.postfixOps
import scala.util.Failure
import scala.util.Success

import org.mockito.Mockito
import org.scalatest.BeforeAndAfterAll
import org.scalatest.Finders
import org.scalatest.WordSpecLike
import org.scalatest.mock.MockitoSugar

import com.convergencelabs.server.db.provision.DomainProvisionerActor.DomainProvisioned
import com.convergencelabs.server.db.provision.DomainProvisionerActor.ProvisionDomain

import akka.actor.ActorSystem
import akka.actor.Status
import akka.testkit.TestKit
import akka.testkit.TestProbe
import scala.util.Failure



class DomainProvisionerActorSpec
    extends TestKit(ActorSystem("DomainProvisionerActor"))
    with WordSpecLike
    with BeforeAndAfterAll
    with MockitoSugar {

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  "A DomainProvisionerActor" when {
    "receiving a ProvisionDomain" must {
      "respond with DomainProvisioned if the provisioing is successful" in new TestFixture {
        Mockito
          .when(provisioner.provisionDomain("dbname", "username", "password", "adminUsername", "adminPassword"))
          .thenReturn(Success(()))

        val client = new TestProbe(system)
        val message = ProvisionDomain("dbname", "username", "password", "adminUsername", "adminPassword")
        domainProvisionerActor.tell(message, client.ref)

        client.expectMsgClass(FiniteDuration(1, TimeUnit.SECONDS), classOf[DomainProvisioned])
      }
      
      "respond with a failure if the provisioing is not successful" in new TestFixture {
        Mockito
          .when(provisioner.provisionDomain("dbname", "username", "password", "adminUsername", "adminPassword"))
          .thenReturn(Failure(new IllegalStateException()))

        val client = new TestProbe(system)
        val message = ProvisionDomain("dbname", "username", "password", "adminUsername", "adminPassword")
        domainProvisionerActor.tell(message, client.ref)

        client.expectMsgClass(FiniteDuration(1, TimeUnit.SECONDS), classOf[Status.Failure])
      }
    }
  }

  trait TestFixture {
    val provisioner = mock[DomainProvisioner]
    val props = DomainProvisionerActor.props(provisioner)
    val domainProvisionerActor = system.actorOf(props)
  }
}
