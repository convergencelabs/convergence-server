package com.convergencelabs.server.db.provision

import com.convergencelabs.server.db.provision.DomainProvisionerActor.DestroyDomain
import com.convergencelabs.server.db.provision.DomainProvisionerActor.DomainDeleted
import com.convergencelabs.server.db.provision.DomainProvisionerActor.DomainProvisioned
import com.convergencelabs.server.db.provision.DomainProvisionerActor.ProvisionDomain

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.Props
import akka.actor.actorRef2Scala
import com.typesafe.config.Config

class DomainProvisionerActor(private[this] val provisioner: DomainProvisioner) extends Actor with ActorLogging {
  def receive: Receive = {
    case provision: ProvisionDomain => provisionDomain(provision)
    case destroy: DestroyDomain => destroyDomain(destroy)
    case message: Any => unhandled(message)
  }

  private[this] def provisionDomain(provision: ProvisionDomain): Unit = {
    val ProvisionDomain(databaseName, dbUsername, dbPassword, dbAdminUsername, dbAdminPassword) = provision
    val currentSender = sender
    // make this asynchronous in the future
    provisioner.provisionDomain(databaseName, dbUsername, dbPassword, dbAdminUsername, dbAdminPassword) map { _ =>
      currentSender ! DomainProvisioned()
    } recover {
      case cause: Exception =>
        currentSender ! akka.actor.Status.Failure(cause)
    }
  }

  private[this] def destroyDomain(destroy: DestroyDomain) = {
    val currentSender = sender
    provisioner.destroyDomain(destroy.databaseUri) map { _ =>
      currentSender ! DomainDeleted()
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
    databaseName: String,
    dbUsername: String,
    dbPassword: String,
    dbAdminUsername: String,
    dbAdminPassword: String)

  case class DomainProvisioned()

  case class DestroyDomain(databaseUri: String)

  case class DomainDeleted()
}
