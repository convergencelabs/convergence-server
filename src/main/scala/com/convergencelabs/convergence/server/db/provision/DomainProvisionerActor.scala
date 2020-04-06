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

import akka.actor.{Actor, ActorLogging, Props, actorRef2Scala}
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.Publish
import com.convergencelabs.convergence.server.domain.DomainId

class DomainProvisionerActor(private[this] val provisioner: DomainProvisioner) 
  extends Actor 
  with ActorLogging {
  
  import DomainProvisionerActor._
  
  private[this] val mediator = DistributedPubSub(context.system).mediator
  
  def receive: Receive = {
    case provision: ProvisionDomain => 
      provisionDomain(provision)
    case destroy: DestroyDomain => 
      destroyDomain(destroy)
    case message: Any => 
      unhandled(message)
  }

  private[this] def provisionDomain(provision: ProvisionDomain): Unit = {
    val ProvisionDomain(fqn, databaseName, dbUsername, dbPassword, dbAdminUsername, dbAdminPassword, anonymousAuth) = provision
    val currentSender = sender
    // make this asynchronous in the future
    provisioner.provisionDomain(fqn, databaseName, dbUsername, dbPassword, dbAdminUsername, dbAdminPassword, anonymousAuth) map { _ =>
      currentSender ! ()
    } recover {
      case cause: Exception =>
        log.error(cause, s"Error provisioning domain: $fqn")
        currentSender ! akka.actor.Status.Failure(cause)
    }
  }

  private[this] def destroyDomain(destroy: DestroyDomain) = {
    val DestroyDomain(domainFqn, databaseUri) = destroy
    val currentSender = sender
    provisioner.destroyDomain(databaseUri) map { _ =>
      val message = DomainDeleted(domainFqn)
      currentSender ! message
      mediator ! Publish(domainTopic(domainFqn), message)
      mediator ! Publish(DomainLifecycleTopic, message)
    } recover {
      case cause: Exception =>
        currentSender ! akka.actor.Status.Failure(cause)
    }
  }
}

object DomainProvisionerActor {

  val RelativePath = "domainProvisioner"

  def props(provisioner: DomainProvisioner): Props = Props(new DomainProvisionerActor(provisioner))

  case class ProvisionDomain(
    domainFqn: DomainId,
    databaseName: String,
    dbUsername: String,
    dbPassword: String,
    dbAdminUsername: String,
    dbAdminPassword: String,
    anonymousAuth: Boolean)

  case class DestroyDomain(domainFqn: DomainId, databaseUri: String)

  case class DomainDeleted(domainFqn: DomainId)
  
  def domainTopic(domainFqn: DomainId): String = {
     s"${domainFqn.namespace}/${domainFqn.domainId}"
  }
  
  def DomainLifecycleTopic = "DomainLifecycle"
}
