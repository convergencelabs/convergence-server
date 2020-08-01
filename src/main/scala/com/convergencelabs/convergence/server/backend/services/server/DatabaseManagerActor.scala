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

import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import com.convergencelabs.convergence.common.Ok
import com.convergencelabs.convergence.server.backend.datastore.convergence.{ConvergenceSchemaDeltaLogEntry, ConvergenceSchemaVersionLogEntry, DomainSchemaDeltaLogEntry, DomainSchemaVersionLogEntry}
import com.convergencelabs.convergence.server.backend.db.schema.{DatabaseManager, DatabaseSchemaStatus}
import com.convergencelabs.convergence.server.model.DomainId
import com.convergencelabs.convergence.server.util.serialization.akka.CborSerializable
import grizzled.slf4j.Logging

private final class DatabaseManagerActor(context: ActorContext[DatabaseManagerActor.Message],
                                         databaseManager: DatabaseManager)
  extends AbstractBehavior[DatabaseManagerActor.Message](context) with Logging {

  import DatabaseManagerActor._

  context.system.receptionist ! Receptionist.Register(DatabaseManagerActor.Key, context.self)

  override def onMessage(msg: DatabaseManagerActor.Message): Behavior[DatabaseManagerActor.Message] = {
    msg match {
      case GetConvergenceSchemaStatusRequest(replyTo) =>
        databaseManager.getConvergenceSchemaStatus()
          .map(status => GetConvergenceSchemaStatusResponse(Right(status)))
          .recover { e =>
            error("Error getting convergence schema status", e)
            GetConvergenceSchemaStatusResponse(Left(UnknownError()))
          }
          .foreach(replyTo ! _)

      case GetConvergenceVersionLogRequest(replyTo) =>
        databaseManager.getConvergenceVersionLog()
          .map(versions => GetConvergenceVersionLogResponse(Right(versions)))
          .recover { e =>
            error("Error getting convergence version log", e)
            GetConvergenceVersionLogResponse(Left(UnknownError()))
          }
          .foreach(replyTo ! _)

      case GetConvergenceDeltaLogRequest(replyTo) =>
        databaseManager.getConvergenceDeltaLog()
          .map(deltas => GetConvergenceDeltaLogResponse(Right(deltas)))
          .recover { e =>
            error("Error getting convergence delta log", e)
            GetConvergenceDeltaLogResponse(Left(UnknownError()))
          }
          .foreach(replyTo ! _)

      case GetDomainSchemaStatusRequest(domainId, replyTo) =>
        databaseManager.getDomainSchemaStatus(domainId)
          .map(status => GetDomainSchemaStatusResponse(Right(status)))
          .recover { e =>
            error("Error getting domain schema status: " + domainId, e)
            GetDomainSchemaStatusResponse(Left(UnknownError()))
          }
          .foreach(replyTo ! _)

      case GetDomainVersionLogRequest(domainId, replyTo) =>
        databaseManager.getDomainVersionLog(domainId)
          .map(versions => GetDomainVersionLogResponse(Right(versions)))
          .recover { e =>
            error("Error getting domain version log: " + domainId, e)
            GetDomainVersionLogResponse(Left(UnknownError()))
          }
          .foreach(replyTo ! _)

      case GetDomainDeltaLogRequest(domainId, replyTo) =>
        databaseManager.getDomainDeltaLog(domainId)
          .map(deltas => GetDomainDeltaLogResponse(Right(deltas)))
          .recover { e =>
            error("Error getting domain delta log: " + domainId, e)
            GetDomainDeltaLogResponse(Left(UnknownError()))
          }
          .foreach(replyTo ! _)

      case UpgradeConvergenceRequest(replyTo) =>
        replyTo ! UpgradeConvergenceResponse(Right(Ok()))
        databaseManager.upgradeConvergence()

      case UpgradeDomainRequest(domainId, replyTo) =>
        replyTo ! UpgradeDomainResponse(Right(Ok()))
        databaseManager.upgradeDomain(domainId)

      case UpgradeDomainsRequest(replyTo) =>
        replyTo ! UpgradeDomainsResponse(Right(Ok()))
        databaseManager.upgradeAllDomains()
    }

    Behaviors.same
  }
}

object DatabaseManagerActor {

  val Key: ServiceKey[Message] = ServiceKey[Message]("DatabaseManagerActor")

  def apply(schemaManager: DatabaseManager): Behavior[Message] =
    Behaviors.setup(context => new DatabaseManagerActor(context, schemaManager))

  /////////////////////////////////////////////////////////////////////////////
  // Message Protocol
  /////////////////////////////////////////////////////////////////////////////

  sealed trait Message extends CborSerializable

  //
  // GetConvergenceSchemaStatus
  //
  final case class GetConvergenceSchemaStatusRequest(replyTo: ActorRef[GetConvergenceSchemaStatusResponse]) extends Message

  final case class GetConvergenceSchemaStatusResponse(status: Either[UnknownError, Option[DatabaseSchemaStatus]]) extends CborSerializable

  //
  // GetConvergenceVersionLog
  //
  final case class GetConvergenceVersionLogRequest(replyTo: ActorRef[GetConvergenceVersionLogResponse]) extends Message

  final case class GetConvergenceVersionLogResponse(versions: Either[UnknownError, List[ConvergenceSchemaVersionLogEntry]]) extends CborSerializable

  //
  // GetConvergenceDeltaLog
  //
  final case class GetConvergenceDeltaLogRequest(replyTo: ActorRef[GetConvergenceDeltaLogResponse]) extends Message

  final case class GetConvergenceDeltaLogResponse(deltas: Either[UnknownError, List[ConvergenceSchemaDeltaLogEntry]]) extends CborSerializable


  //
  // GetDomainSchemaStatus
  //
  final case class GetDomainSchemaStatusRequest(domainId: DomainId, replyTo: ActorRef[GetDomainSchemaStatusResponse]) extends Message

  final case class GetDomainSchemaStatusResponse(status: Either[UnknownError, Option[DatabaseSchemaStatus]]) extends CborSerializable

  //
  // GetDomainVersionLog
  //
  final case class GetDomainVersionLogRequest(domainId: DomainId, replyTo: ActorRef[GetDomainVersionLogResponse]) extends Message

  final case class GetDomainVersionLogResponse(versions: Either[UnknownError, List[DomainSchemaVersionLogEntry]]) extends CborSerializable

  //
  // GetDomainDeltaLog
  //
  final case class GetDomainDeltaLogRequest(domainId: DomainId, replyTo: ActorRef[GetDomainDeltaLogResponse]) extends Message

  final case class GetDomainDeltaLogResponse(deltas: Either[UnknownError, List[DomainSchemaDeltaLogEntry]]) extends CborSerializable


  //
  // UpgradeConvergence
  //
  final case class UpgradeConvergenceRequest(replyTo: ActorRef[UpgradeConvergenceResponse]) extends Message

  final case class UpgradeConvergenceResponse(response: Either[UnknownError, Ok]) extends CborSerializable

  //
  // UpgradeDomain
  //
  final case class UpgradeDomainRequest(id: DomainId, replyTo: ActorRef[UpgradeDomainResponse]) extends Message

  final case class UpgradeDomainResponse(response: Either[UnknownError, Ok]) extends CborSerializable

  //
  // UpgradeDomainsRequest
  //
  final case class UpgradeDomainsRequest(replyTo: ActorRef[UpgradeDomainsResponse]) extends Message

  final case class UpgradeDomainsResponse(response: Either[UnknownError, Ok]) extends CborSerializable

  final case class UnknownError()

}
