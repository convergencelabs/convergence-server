/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

package com.convergencelabs.server.db.provision

import java.util.concurrent.TimeUnit

import scala.concurrent.duration.FiniteDuration
import scala.language.postfixOps
import scala.util.Failure
import scala.util.Success

import org.mockito.Mockito
import org.scalatest.BeforeAndAfterAll
import org.scalatest.WordSpecLike
import org.scalatest.mockito.MockitoSugar

import com.convergencelabs.server.db.provision.DomainProvisionerActor.ProvisionDomain
import com.convergencelabs.server.domain.DomainId

import akka.actor.ActorSystem
import akka.testkit.TestKit
import akka.testkit.TestProbe
import akka.actor.Status


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

        client.expectMsgClass(FiniteDuration(1, TimeUnit.SECONDS), classOf[Unit])
      }
      
      "respond with a failure if the provisioing is not successful" in new TestFixture {
        Mockito
          .when(provisioner.provisionDomain(domainFqn, "dbname", "username", "password", "adminUsername", "adminPassword", false))
          .thenReturn(Failure(new IllegalStateException()))

        val client = new TestProbe(system)
        val message = ProvisionDomain(domainFqn, "dbname", "username", "password", "adminUsername", "adminPassword", false)
        domainProvisionerActor.tell(message, client.ref)

        client.expectMsgClass(FiniteDuration(1, TimeUnit.SECONDS), classOf[Status.Failure])
      }
    }
  }

  trait TestFixture {
    val provisioner = mock[DomainProvisioner]
    val props = DomainProvisionerActor.props(provisioner)
    val domainProvisionerActor = system.actorOf(props)
    val domainFqn = DomainId("some", "domain")
  }
}
