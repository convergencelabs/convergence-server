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

package com.convergencelabs.convergence.server.db.provision

import java.util.concurrent.TimeUnit

import scala.concurrent.duration.FiniteDuration
import scala.language.postfixOps
import scala.util.Failure
import scala.util.Success
import org.mockito.Mockito
import org.scalatest.BeforeAndAfterAll
import org.scalatest.WordSpecLike
import org.scalatest.mockito.MockitoSugar
import com.convergencelabs.convergence.server.db.provision.DomainProvisionerActor.ProvisionDomain
import com.convergencelabs.convergence.server.domain.DomainId
import akka.actor.{ActorRef, ActorSystem, Props, Status}
import akka.testkit.TestKit
import akka.testkit.TestProbe


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
          .when(provisioner.provisionDomain(domainFqn, "dbname", "username", "password", "adminUsername", "adminPassword", false))
          .thenReturn(Success(()))

        val client = new TestProbe(system)
        val message = ProvisionDomain(domainFqn, "dbname", "username", "password", "adminUsername", "adminPassword", false)
        domainProvisionerActor.tell(message, client.ref)

        client.expectMsgClass(FiniteDuration(1, TimeUnit.SECONDS), classOf[Status.Success])
      }
      
      "respond with a failure if the provisioing is not successful" in new TestFixture {
        Mockito
          .when(provisioner.provisionDomain(domainFqn, "dbname", "username", "password", "adminUsername", "adminPassword", false))
          .thenReturn(Failure(new IllegalStateException("Induced error for testing")))

        val client = new TestProbe(system)
        val message = ProvisionDomain(domainFqn, "dbname", "username", "password", "adminUsername", "adminPassword", false)
        domainProvisionerActor.tell(message, client.ref)

        client.expectMsgClass(FiniteDuration(1, TimeUnit.SECONDS), classOf[Status.Failure])
      }
    }
  }

  trait TestFixture {
    val provisioner: DomainProvisioner = mock[DomainProvisioner]
    val props: Props = DomainProvisionerActor.props(provisioner)
    val domainProvisionerActor: ActorRef = system.actorOf(props)
    val domainFqn: DomainId = DomainId("some", "domain")
  }
}
