package com.convergencelabs.server.db.data

import scala.util.Failure
import scala.util.Success

import com.convergencelabs.server.datastore.DomainDBProvider
import com.convergencelabs.server.datastore.domain.DomainPersistenceProvider
import com.convergencelabs.server.domain.DomainFqn
import com.orientechnologies.orient.core.db.OPartitionedDatabasePool

import ConvergenceImporterActor.ConvergenceImport
import ConvergenceImporterActor.DomainExport
import ConvergenceImporterActor.DomainExportResponse
import ConvergenceImporterActor.DomainImport
import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.Props
import akka.actor.actorRef2Scala

class ConvergenceImporterActor(
    private[this] val dbBaseUri: String,
    private[this] val dbPool: OPartitionedDatabasePool,
    private[this] val domainProvisioner: ActorRef) extends Actor with ActorLogging {

  val domainDbProvider = new DomainDBProvider(dbBaseUri, dbPool)

  def receive: Receive = {
    case ConvergenceImport(script) => importConvergence(script)
    case DomainImport(fqn, script) => importDomain(fqn, script)
    case DomainExport(fqn) => exportDomain(fqn)
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
        domainPool.close()
      }
    }
  }
  
  private[this] def exportDomain(fqn: DomainFqn): Unit = {
    log.debug(s"Exporting domain: ${fqn.namespace}/${fqn.domainId}")
    domainDbProvider.getDomainDBPool(fqn) foreach {
      _.map { domainPool =>
        val provider = new DomainPersistenceProvider(domainPool)
        val domainExporter = new DomainExporter(provider)
        // FIXME handle error
        domainExporter.exportDomain() match {
          case Success(export) =>
            sender ! DomainExportResponse(export)
          case Failure(f) =>
            sender ! akka.actor.Status.Failure(f)
        }
        domainPool.close()
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
  case class DomainExport(domainFqn: DomainFqn)
  case class DomainExportResponse(script: DomainScript)
}
