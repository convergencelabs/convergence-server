package com.convergencelabs.server.db.data

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.Props
import akka.actor.actorRef2Scala
import com.typesafe.config.Config
import ConvergenceImporterActor._
import akka.actor.ActorRef
import com.orientechnologies.orient.core.db.OPartitionedDatabasePool
import com.convergencelabs.server.schema.DomainDBProvider
import com.convergencelabs.server.domain.DomainFqn
import com.convergencelabs.server.datastore.domain.DomainPersistenceProvider

class ConvergenceImporterActor(
    private[this] val dbBaseUri: String,
    private[this] val dbPool: OPartitionedDatabasePool,
    private[this] val domainProvisioner: ActorRef) extends Actor with ActorLogging {

  val domainDbProvider = new DomainDBProvider(dbBaseUri, dbPool)

  def receive: Receive = {
    case ConvergenceImport(script) => importConvergence(script)
    case DomainImport(fqn, script) => importDomain(fqn, script)
    case message: Any => unhandled(message)
  }

  private[this] def importConvergence(script: ConvergenceScript): Unit = {
    val importer = new ConvergenceImporter(
      dbBaseUri,
      dbPool,
      domainProvisioner,
      script,
      context.system.dispatcher)
    importer.importData() map { _ => 
      log.debug("Import completed successfuly")
    } recover {
      case cause: Exception =>
        log.error(cause, "Data import failed")
    }
    
    sender ! (())
  }

  private[this] def importDomain(fqn: DomainFqn, script: DomainScript): Unit = {
    domainDbProvider.getDomainDBPool(fqn) foreach {
      _.map { domainPool =>
        val provider = new DomainPersistenceProvider(domainPool)
        val domainImporter = new DomainImporter(provider, script)
        // FIXME handle error
        domainImporter.importDomain()
      }
    }
  }
}

object ConvergenceImporterActor {

  val RelativePath = "convergenceImporter"

  def props(
    dbBaseUri: String,
    dbPool: OPartitionedDatabasePool,
    domainProvisioner: ActorRef): Props =
    Props(new ConvergenceImporterActor(dbBaseUri, dbPool, domainProvisioner))

  case class ConvergenceImport(script: ConvergenceScript)
  case class DomainImport(domainFqn: DomainFqn, script: DomainScript)
}
