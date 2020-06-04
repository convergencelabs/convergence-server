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

import akka.actor.testkit.typed.scaladsl.{ScalaTestWithActorTestKit, TestProbe}
import com.convergencelabs.convergence.server.db.provision.DomainProvisioner.ProvisionRequest
import com.convergencelabs.convergence.server.db.provision.DomainProvisionerActor.{ProvisionDomain, ProvisionDomainResponse}
import com.convergencelabs.convergence.server.domain.DomainId
import org.mockito.Mockito
import org.scalatest.BeforeAndAfterAll
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatestplus.mockito.MockitoSugar

import scala.concurrent.duration.FiniteDuration
import scala.language.postfixOps
import scala.util.{Failure, Success}

class DomainProvisionerActorSpec
    extends ScalaTestWithActorTestKit
    with AnyWordSpecLike
    with BeforeAndAfterAll
    with MockitoSugar {

  override def afterAll(): Unit = testKit.shutdownTestKit()

  "A DomainProvisionerActor" when {
    "receiving a ProvisionDomain" must {
      "respond with DomainProvisioned if the provisioning is successful" in new TestFixture {
        Mockito
          .when(provisioner.provisionDomain(ProvisionRequest(domainFqn, "dbname", "username", "password", "adminUsername", "adminPassword", anonymousAuth = false)))
          .thenReturn(Success(()))

        val client = testKit.createTestProbe[ProvisionDomainResponse]()
        val message = ProvisionDomain(
          ProvisionRequest(domainFqn, "dbname", "username", "password", "adminUsername", "adminPassword", anonymousAuth = false),
          client.ref)
        domainProvisionerActor ! message

        val response: ProvisionDomainResponse = client.expectMessageType(FiniteDuration(1, TimeUnit.SECONDS))
        assert(response.response.isRight)
      }
      
      "respond with a failure if the provisioning is not successful" in new TestFixture {
        Mockito
          .when(provisioner.provisionDomain(ProvisionRequest(domainFqn, "dbname", "username", "password", "adminUsername", "adminPassword", anonymousAuth = false)))
          .thenReturn(Failure(new IllegalStateException("Induced error for testing")))

        val client = testKit.createTestProbe[ProvisionDomainResponse]()
        val message = ProvisionDomain(ProvisionRequest(domainFqn, "dbname", "username", "password", "adminUsername", "adminPassword", anonymousAuth = false), client.ref)
        domainProvisionerActor ! message

        val response: ProvisionDomainResponse = client.expectMessageType(FiniteDuration(1, TimeUnit.SECONDS))

        assert(response.response.isLeft)
      }
    }
  }

  trait TestFixture {
    val domainFqn: DomainId = DomainId("some", "domain")
    val provisioner: DomainProvisioner = mock[DomainProvisioner]
    val domainLifecycleTopic: TestProbe[DomainLifecycleTopic.TopicMessage] = testKit.createTestProbe()
    val behavior = DomainProvisionerActor(provisioner, domainLifecycleTopic.ref)
    val domainProvisionerActor = testKit.spawn(behavior)
  }
}
