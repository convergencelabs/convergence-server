/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

package com.convergencelabs.server.db.provision

import com.convergencelabs.server.db.provision.DomainProvisionerActor.DestroyDomain
import com.convergencelabs.server.db.provision.DomainProvisionerActor.ProvisionDomain
import com.convergencelabs.server.domain.DomainId

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.Props
import akka.actor.actorRef2Scala
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.Publish

class DomainProvisionerActor(private[this] val provisioner: DomainProvisioner) 
  extends Actor 
  with ActorLogging {
  
  import DomainProvisionerActor._
  
  val mediator = DistributedPubSub(context.system).mediator
  
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
      currentSender ! (())
    } recover {
      case cause: Exception =>
        log.error(cause, s"Error provisioning domain: ${fqn}")
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
  
  def domainTopic(domainFqn: DomainId) = {
     s"${domainFqn.namespace}/${domainFqn.domainId}"
  }
  
  def DomainLifecycleTopic = "DomainLifecycle"
}
