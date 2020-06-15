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

import akka.actor.typed.receptionist.ServiceKey
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import com.convergencelabs.convergence.common.Ok
import com.convergencelabs.convergence.server.actor.CborSerializable
import com.convergencelabs.convergence.server.datastore.convergence.DomainStoreActor
import com.convergencelabs.convergence.server.datastore.domain.DomainPersistenceProviderImpl
import com.convergencelabs.convergence.server.db.data.ConvergenceImporterActor.{ConvergenceExportRequest, _}
import com.convergencelabs.convergence.server.db.{DatabaseProvider, DomainDatabaseFactory}
import com.convergencelabs.convergence.server.domain.DomainId
import grizzled.slf4j.Logging

class ConvergenceImporterActor(context: ActorContext[ConvergenceImporterActor.Message],
                               dbBaseUri: String,
                               dbProvider: DatabaseProvider,
                               domainStoreActor: ActorRef[DomainStoreActor.Message])
  extends AbstractBehavior[ConvergenceImporterActor.Message](context) with Logging {

  val domainDbProvider = new DomainDatabaseFactory(dbBaseUri, dbProvider)

  override def onMessage(msg: Message): Behavior[Message] = {
    msg match {
      case msg: ConvergenceImportRequest =>
        importConvergence(msg)
      case msg: ConvergenceExportRequest =>
        exportConvergenceUser(msg)
      case msg: DomainImportRequest =>
        importDomain(msg)
      case msg: DomainExportRequest =>
        exportDomain(msg)
    }

    Behaviors.same
  }


  private[this] def importConvergence(msg: ConvergenceImportRequest): Unit = {
    val ConvergenceImportRequest(script, replyTo) = msg
    val importer = new ConvergenceImporter(
      dbBaseUri,
      dbProvider,
      domainStoreActor,
      script,
      context.executionContext,
      context.system)
    importer.importData()
      .map { _ =>
        debug("Import completed successfully")
        ConvergenceImportResponse(Right(Ok()))
      }
      .recover {
        case cause: Exception =>
          error("Data import failed", cause)
          ConvergenceImportResponse(Left(UnknownError()))
      }
      .foreach(replyTo ! _)
  }

  private[this] def exportConvergenceUser(msg: ConvergenceExportRequest): Unit = {
    val ConvergenceExportRequest(username, replyTo) = msg
    debug(s"Exporting convergence user: $username")
    val exporter = new ConvergenceExporter(dbBaseUri, dbProvider)
    exporter.exportData(username)
      .map(export => ConvergenceExportResponse(Right(export)))
      .recover { cause =>
        error("error exporting domain", cause)
        ConvergenceExportResponse(Left(UnknownError()))
      }
      .foreach(replyTo ! _)
  }

  private[this] def importDomain(msg: DomainImportRequest): Unit = {
    val DomainImportRequest(id, script, replyTo) = msg
    // FIXME should this be a flatMap or something?
    domainDbProvider.getDomainDatabasePool(id) foreach {
      domainPool =>
        val provider = new DomainPersistenceProviderImpl(id, domainPool)
        val domainImporter = new DomainImporter(provider, script)
        // FIXME handle error
        domainImporter.importDomain()
        domainPool.shutdown()
    }

    // FIXME errors??
    replyTo ! DomainImportResponse(Right(Ok()))
  }

  private[this] def exportDomain(msg: DomainExportRequest): Unit = {
    val DomainExportRequest(domainId, replyTo) = msg
    debug(s"Exporting domain: ${domainId.namespace}/${domainId.domainId}")
    domainDbProvider.getDomainDatabasePool(domainId) foreach {
      domainPool =>
        val provider = new DomainPersistenceProviderImpl(domainId, domainPool)
        val domainExporter = new DomainExporter(provider)
        domainExporter.exportDomain()
          .map(export => DomainExportResponse(Right(export)))
          .recover { cause =>
            error("error exporting domain", cause)
            DomainExportResponse(Left(UnknownError()))
          }
          .foreach(replyTo ! _)

        domainPool.shutdown()
    }
  }
}

object ConvergenceImporterActor {
  val Key: ServiceKey[Message] = ServiceKey[Message]("ConvergenceImporterActor")

  def apply(dbBaseUri: String,
            dbProvider: DatabaseProvider,
            domainStoreActor: ActorRef[DomainStoreActor.Message]): Behavior[Message] =
    Behaviors.setup(context => new ConvergenceImporterActor(context, dbBaseUri, dbProvider, domainStoreActor))

  sealed trait Message extends CborSerializable

  //
  // ConvergenceImport
  //
  final case class ConvergenceImportRequest(script: ConvergenceScript, replyTo: ActorRef[ConvergenceImportResponse]) extends Message

  final case class ConvergenceImportResponse(response: Either[UnknownError, Ok]) extends CborSerializable

  //
  // DomainImport
  //
  final case class DomainImportRequest(domainFqn: DomainId, script: DomainScript, replyTo: ActorRef[DomainImportResponse]) extends Message

  final case class DomainImportResponse(response: Either[UnknownError, Ok]) extends CborSerializable

  //
  // ConvergenceExport
  //
  final case class ConvergenceExportRequest(username: String, replyTo: ActorRef[ConvergenceExportResponse]) extends Message

  final case class ConvergenceExportResponse(script: Either[UnknownError, ConvergenceScript]) extends CborSerializable

  //
  // DomainExport
  //
  final case class DomainExportRequest(domainId: DomainId, replyTo: ActorRef[DomainExportResponse]) extends Message

  final case class DomainExportResponse(script: Either[UnknownError, DomainScript]) extends CborSerializable

  final case class UnknownError()
}
