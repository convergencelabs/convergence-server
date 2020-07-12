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
import com.convergencelabs.convergence.server.backend.db.schema.DatabaseManager
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
      case GetConvergenceVersionRequest(replyTo) =>
        databaseManager.getConvergenceVersion()
          .map(version => GetConvergenceVersionResponse(Right(version)))
          .recover(_ => GetConvergenceVersionResponse(Left(UnknownError())))
          .foreach(replyTo ! _)

      case GetDomainVersionRequest(fqn, replyTo) =>
        // TODO handle Domain Not Found in the get domain version
        databaseManager.getDomainVersion(fqn)
          .map(version => GetDomainVersionResponse(Right(version)))
          .recover(_ => GetDomainVersionResponse(Left(UnknownError())))
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
  // GetConvergenceVersion
  //
  final case class GetConvergenceVersionRequest(replyTo: ActorRef[GetConvergenceVersionResponse]) extends Message

  final case class GetConvergenceVersionResponse(version: Either[UnknownError, Option[String]]) extends CborSerializable


  //
  // GetConvergenceVersion
  //
  final case class GetDomainVersionRequest(domainId: DomainId, replyTo: ActorRef[GetDomainVersionResponse]) extends Message

  final case class GetDomainVersionResponse(version: Either[UnknownError, Option[String]]) extends CborSerializable

  //
  // GetConvergenceVersion
  //
  final case class UpgradeConvergenceRequest(replyTo: ActorRef[UpgradeConvergenceResponse]) extends Message

  final case class UpgradeConvergenceResponse(response: Either[UnknownError, Ok]) extends CborSerializable

  //
  // GetConvergenceVersion
  //
  final case class UpgradeDomainRequest(id: DomainId, replyTo: ActorRef[UpgradeDomainResponse]) extends Message

  final case class UpgradeDomainResponse(response: Either[UnknownError, Ok]) extends CborSerializable

  //
  // GetConvergenceVersion
  //
  final case class UpgradeDomainsRequest(replyTo: ActorRef[UpgradeDomainsResponse]) extends Message

  final case class UpgradeDomainsResponse(response: Either[UnknownError, Ok]) extends CborSerializable

  final case class UnknownError()

}
