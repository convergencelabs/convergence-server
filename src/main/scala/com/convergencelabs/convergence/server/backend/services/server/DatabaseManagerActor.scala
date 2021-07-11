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

import akka.actor.typed.pubsub.Topic.Publish
import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import com.convergencelabs.convergence.common.Ok
import com.convergencelabs.convergence.server.backend.datastore.EntityNotFoundException
import com.convergencelabs.convergence.server.backend.datastore.convergence.{ConvergenceSchemaDeltaLogEntry, ConvergenceSchemaVersionLogEntry, DomainSchemaDeltaLogEntry, DomainSchemaVersionLogEntry, DomainStore}
import com.convergencelabs.convergence.server.backend.db.schema.{DatabaseManager, DatabaseSchemaStatus}
import com.convergencelabs.convergence.server.backend.services.server.DomainLifecycleTopic.DomainStatusChanged
import com.convergencelabs.convergence.server.model.DomainId
import com.convergencelabs.convergence.server.model.server.domain.DomainStatus
import com.convergencelabs.convergence.server.util.serialization.akka.CborSerializable
import com.fasterxml.jackson.annotation.{JsonSubTypes, JsonTypeInfo}
import grizzled.slf4j.Logging

private final class DatabaseManagerActor(context: ActorContext[DatabaseManagerActor.Message],
                                         databaseManager: DatabaseManager,
                                         domainStore: DomainStore,
                                         domainLifecycleTopic: ActorRef[DomainLifecycleTopic.TopicMessage])
  extends AbstractBehavior[DatabaseManagerActor.Message](context) with Logging {

  import DatabaseManagerActor._

  context.system.receptionist ! Receptionist.Register(DatabaseManagerActor.Key, context.self)

  override def onMessage(msg: DatabaseManagerActor.Message): Behavior[DatabaseManagerActor.Message] = {
    msg match {
      case GetConvergenceSchemaStatusRequest(replyTo) =>
        databaseManager.getConvergenceSchemaStatus()
          .map {
            case None =>
              Left(ConvergenceSchemaNotInstalledError())
            case Some(status) =>
              Right(status)
          }
          .recover { e =>
            error("Error getting convergence schema status", e)
            Left(UnknownError())
          }
          .foreach(replyTo ! GetConvergenceSchemaStatusResponse(_))

      case GetConvergenceVersionLogRequest(replyTo) =>
        databaseManager.getConvergenceVersionLog()
          .map(versions => Right(versions))
          .recover { e =>
            error("Error getting convergence version log", e)
            Left(UnknownError())
          }
          .foreach(replyTo ! GetConvergenceVersionLogResponse(_))

      case GetConvergenceDeltaLogRequest(replyTo) =>
        databaseManager.getConvergenceDeltaLog()
          .map(deltas => Right(deltas))
          .recover { e =>
            error("Error getting convergence delta log", e)
            Left(UnknownError())
          }
          .foreach(replyTo ! GetConvergenceDeltaLogResponse(_))

      case GetDomainSchemaStatusRequest(domainId, replyTo) =>
        databaseManager.getDomainSchemaStatus(domainId)
          .map(status => Right(status))
          .recover {
            case _: EntityNotFoundException =>
              Left(DomainNotFoundError())
            case e =>
              error("Error getting domain schema status: " + domainId, e)
              Left(UnknownError())
          }
          .foreach(replyTo ! GetDomainSchemaStatusResponse(_))

      case GetDomainVersionLogRequest(domainId, replyTo) =>
        databaseManager.getDomainVersionLog(domainId)
          .map(versions => Right(versions))
          .recover { e =>
            error("Error getting domain version log: " + domainId, e)
            Left(UnknownError())
          }
          .foreach(replyTo ! GetDomainVersionLogResponse(_))

      case GetDomainDeltaLogRequest(domainId, replyTo) =>
        databaseManager.getDomainDeltaLog(domainId)
          .map(deltas => Right(deltas))
          .recover { e =>
            error("Error getting domain delta log: " + domainId, e)
            Left(UnknownError())
          }
          .foreach(replyTo ! GetDomainDeltaLogResponse(_))

      case UpgradeConvergenceRequest(replyTo) =>
        replyTo ! UpgradeConvergenceResponse(Right(Ok()))
        databaseManager.upgradeConvergence()

      case UpgradeDomainRequest(domainId, replyTo) =>
        domainLifecycleTopic ! Publish(DomainStatusChanged(domainId, DomainStatus.SchemaUpgrading))

        replyTo ! UpgradeDomainResponse(Right(Ok()))

        databaseManager.upgradeDomain(domainId)

        // No matter what happened, we want to broadcast the current status.
        domainStore.getDomain(domainId)
          .map { domain =>
            domainLifecycleTopic ! Publish(DomainStatusChanged(domainId, domain.status))
          }
          .recover { e =>
            error(s"Could not get the current domain status after a domain upgrade for domain: ${domainId.namespace}/${domainId.domainId}", e)
          }
    }

    Behaviors.same
  }
}

object DatabaseManagerActor {

  val Key: ServiceKey[Message] = ServiceKey[Message]("DatabaseManagerActor")

  def apply(schemaManager: DatabaseManager,
            domainStore: DomainStore,
            domainLifecycleTopic: ActorRef[DomainLifecycleTopic.TopicMessage]): Behavior[Message] =
    Behaviors.setup(context => new DatabaseManagerActor(context, schemaManager, domainStore, domainLifecycleTopic))

  /////////////////////////////////////////////////////////////////////////////
  // Message Protocol
  /////////////////////////////////////////////////////////////////////////////

  sealed trait Message extends CborSerializable

  //
  // GetConvergenceSchemaStatus
  //
  final case class GetConvergenceSchemaStatusRequest(replyTo: ActorRef[GetConvergenceSchemaStatusResponse]) extends Message

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[ConvergenceSchemaNotInstalledError], name = "not_install"),
    new JsonSubTypes.Type(value = classOf[UnknownError], name = "unknown")
  ))
  sealed trait GetConvergenceSchemaStatusError

  final case class ConvergenceSchemaNotInstalledError() extends GetConvergenceSchemaStatusError

  final case class GetConvergenceSchemaStatusResponse(status: Either[GetConvergenceSchemaStatusError, DatabaseSchemaStatus]) extends CborSerializable

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

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[DomainNotFoundError], name = "not_found"),
    new JsonSubTypes.Type(value = classOf[UnknownError], name = "unknown")
  ))
  sealed trait GetDomainSchemaStatusError

  final case class GetDomainSchemaStatusResponse(status: Either[GetDomainSchemaStatusError, DatabaseSchemaStatus]) extends CborSerializable

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

  final case class UnknownError() extends AnyRef
    with GetDomainSchemaStatusError
    with GetConvergenceSchemaStatusError

  final case class DomainNotFoundError() extends AnyRef
    with GetDomainSchemaStatusError
}
