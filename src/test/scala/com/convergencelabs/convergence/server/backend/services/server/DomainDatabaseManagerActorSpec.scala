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

package com.convergencelabs.convergence.server.backend.services.server

import java.util.concurrent.TimeUnit

import akka.actor.testkit.typed.scaladsl.{ScalaTestWithActorTestKit, TestProbe}
import com.convergencelabs.convergence.server.InducedTestingException
import com.convergencelabs.convergence.server.backend.db.DomainDatabaseManager
import com.convergencelabs.convergence.server.backend.db.DomainDatabaseManager.DomainDatabaseCreationData
import com.convergencelabs.convergence.server.backend.services.server.DomainDatabaseManagerActor.{CreateDomainDatabaseRequest, CreateDomainDatabaseResponse}
import com.convergencelabs.convergence.server.model.DomainId
import org.mockito.Mockito
import org.scalatest.BeforeAndAfterAll
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatestplus.mockito.MockitoSugar

import scala.concurrent.duration.FiniteDuration
import scala.language.postfixOps
import scala.util.{Failure, Success}

class DomainDatabaseManagerActorSpec
    extends ScalaTestWithActorTestKit
    with AnyWordSpecLike
    with BeforeAndAfterAll
    with MockitoSugar {

  override def afterAll(): Unit = testKit.shutdownTestKit()

  "A DomainDatabaseManagerActor" when {
    "receiving a ProvisionDomain" must {
      "respond with CreateDomainDatabaseResponse if the creation is successful" in new TestFixture {
        Mockito
          .when(domainData.createDomainDatabase(DomainDatabaseCreationData
          (domainFqn, "dbname", "username", "password", "adminUsername", "adminPassword", anonymousAuth = false)))
          .thenReturn(Success(()))

        val client = testKit.createTestProbe[CreateDomainDatabaseResponse]()
        val message = CreateDomainDatabaseRequest(
          DomainDatabaseCreationData(domainFqn, "dbname", "username", "password", "adminUsername", "adminPassword", anonymousAuth = false),
          client.ref)
        domainDbManagerActor ! message

        val response: CreateDomainDatabaseResponse = client.expectMessageType[CreateDomainDatabaseResponse](FiniteDuration(1, TimeUnit.SECONDS))
        assert(response.response.isRight)
      }

      "respond with a failure if the provisioning is not successful" in new TestFixture {
        Mockito
          .when(domainData.createDomainDatabase(
            DomainDatabaseCreationData(domainFqn, "dbname", "username", "password", "adminUsername", "adminPassword", anonymousAuth = false)))
          .thenReturn(Failure(InducedTestingException()))

        val client = testKit.createTestProbe[CreateDomainDatabaseResponse]()
        val message = CreateDomainDatabaseRequest(DomainDatabaseCreationData(domainFqn, "dbname", "username", "password", "adminUsername", "adminPassword", anonymousAuth = false), client.ref)
        domainDbManagerActor ! message

        val response: CreateDomainDatabaseResponse = client.expectMessageType[CreateDomainDatabaseResponse](FiniteDuration(1, TimeUnit.SECONDS))

        assert(response.response.isLeft)
      }
    }
  }

  trait TestFixture {
    val domainFqn: DomainId = DomainId("some", "domain")
    val domainData: DomainDatabaseManager = mock[DomainDatabaseManager]
    val domainLifecycleTopic: TestProbe[DomainLifecycleTopic.TopicMessage] = testKit.createTestProbe()
    val behavior = DomainDatabaseManagerActor(domainData, domainLifecycleTopic.ref)
    val domainDbManagerActor = testKit.spawn(behavior)
  }
}
