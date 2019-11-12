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

package com.convergencelabs.convergence.server.db.data

import scala.util.Failure
import scala.util.Success

import com.convergencelabs.convergence.server.db.DatabaseProvider
import com.convergencelabs.convergence.server.db.DomainDatabaseFactory
import com.convergencelabs.convergence.server.datastore.domain.DomainPersistenceProvider
import com.convergencelabs.convergence.server.db.data.ConvergenceImporterActor.ConvergenceExport
import com.convergencelabs.convergence.server.db.data.ConvergenceImporterActor.ConvergenceExportResponse
import com.convergencelabs.convergence.server.domain.DomainId

import ConvergenceImporterActor.ConvergenceImport
import ConvergenceImporterActor.DomainExport
import ConvergenceImporterActor.DomainExportResponse
import ConvergenceImporterActor.DomainImport
import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.Props
import akka.actor.actorRef2Scala
import com.convergencelabs.convergence.server.datastore.domain.DomainPersistenceProviderImpl

class ConvergenceImporterActor(
    private[this] val dbBaseUri: String,
    private[this] val dbProvider: DatabaseProvider,
    private[this] val domainProvisioner: ActorRef) extends Actor with ActorLogging {

  val domainDbProvider = new DomainDatabaseFactory(dbBaseUri, dbProvider)

  def receive: Receive = {
    case ConvergenceImport(script) =>
      importConvergence(script)
    case ConvergenceExport(Some(username)) =>
      exportConvergenceUser(username)
    case DomainImport(fqn, script) =>
      importDomain(fqn, script)
    case DomainExport(fqn) =>
      exportDomain(fqn)
    case message: Any => unhandled(message)
  }

  private[this] def importConvergence(script: ConvergenceScript): Unit = {
    val importer = new ConvergenceImporter(
      dbBaseUri,
      dbProvider,
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

  private[this] def exportConvergenceUser(username: String): Unit = {
    log.debug(s"Exporting convergence user: ${username}")
    val exporter = new ConvergenceExporter(dbBaseUri, dbProvider)
    exporter.exportData(username) match {
      case Success(script) =>
        sender ! ConvergenceExportResponse(script)
      case Failure(cause) =>
        sender ! akka.actor.Status.Failure(cause)
    }
  }

  private[this] def importDomain(fqn: DomainId, script: DomainScript): Unit = {
    // FIXME should this be a flatMap or something?
    domainDbProvider.getDomainDatabasePool(fqn) foreach {
      domainPool =>
        val provider = new DomainPersistenceProviderImpl(domainPool)
        val domainImporter = new DomainImporter(provider, script)
        // FIXME handle error
        domainImporter.importDomain()
        domainPool.shutdown()
    }
  }

  private[this] def exportDomain(fqn: DomainId): Unit = {
    log.debug(s"Exporting domain: ${fqn.namespace}/${fqn.domainId}")
    domainDbProvider.getDomainDatabasePool(fqn) foreach {
      domainPool =>
        val provider = new DomainPersistenceProviderImpl(domainPool)
        val domainExporter = new DomainExporter(provider)
        // FIXME handle error
        domainExporter.exportDomain() match {
          case Success(export) =>
            sender ! DomainExportResponse(export)
          case Failure(f) =>
            sender ! akka.actor.Status.Failure(f)
        }
        domainPool.shutdown()
    }
  }
}

object ConvergenceImporterActor {

  val RelativePath = "convergenceImporter"

  def props(
    dbBaseUri: String,
    dbProvider: DatabaseProvider,
    domainProvisioner: ActorRef): Props =
    Props(new ConvergenceImporterActor(dbBaseUri, dbProvider, domainProvisioner))

  case class ConvergenceImport(script: ConvergenceScript)
  case class DomainImport(domainFqn: DomainId, script: DomainScript)

  case class ConvergenceExport(username: Option[String])
  case class ConvergenceExportResponse(script: ConvergenceScript)

  case class DomainExport(domainFqn: DomainId)
  case class DomainExportResponse(script: DomainScript)
}
