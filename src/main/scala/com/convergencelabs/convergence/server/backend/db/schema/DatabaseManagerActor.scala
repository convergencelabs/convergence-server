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

package com.convergencelabs.convergence.server.backend.db.schema

import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import com.convergencelabs.convergence.common.Ok
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

      case UpgradeConvergenceRequest(version, preRelease, replyTo) =>
        replyTo ! UpgradeConvergenceResponse(Right(Ok()))

        version match {
          case Some(v) =>
            databaseManager.updagradeConvergence(v, preRelease)
          case None =>
            databaseManager.updagradeConvergenceToLatest(preRelease)
        }

      case UpgradeDomainRequest(fqn, version, preRelease, replyTo) =>
        replyTo ! UpgradeDomainResponse(Right(Ok()))

        version match {
          case Some(v) =>
            databaseManager.upgradeDomain(fqn, v, preRelease)
          case None =>
            databaseManager.upgradeDomainToLatest(fqn, preRelease)
        }

      case UpgradeDomainsRequest(version, preRelease, replyTo) =>
        replyTo ! UpgradeDomainsResponse(Right(Ok()))

        version match {
          case Some(v) =>
            databaseManager.upgradeAllDomains(v, preRelease)
          case None =>
            databaseManager.upgradeAllDomainsToLatest(preRelease)
        }
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

  final case class GetConvergenceVersionResponse(version: Either[UnknownError, Int]) extends CborSerializable


  //
  // GetConvergenceVersion
  //
  final case class GetDomainVersionRequest(domainId: DomainId, replyTo: ActorRef[GetDomainVersionResponse]) extends Message

  final case class GetDomainVersionResponse(version: Either[UnknownError, Int]) extends CborSerializable

  //
  // GetConvergenceVersion
  //
  final case class UpgradeConvergenceRequest(version: Option[Int], preRelease: Boolean, replyTo: ActorRef[UpgradeConvergenceResponse]) extends Message

  final case class UpgradeConvergenceResponse(response: Either[UnknownError, Ok]) extends CborSerializable

  //
  // GetConvergenceVersion
  //
  final case class UpgradeDomainRequest(id: DomainId, version: Option[Int], preRelease: Boolean, replyTo: ActorRef[UpgradeDomainResponse]) extends Message

  final case class UpgradeDomainResponse(response: Either[UnknownError, Ok]) extends CborSerializable

  //
  // GetConvergenceVersion
  //
  final case class UpgradeDomainsRequest(version: Option[Int], preRelease: Boolean, replyTo: ActorRef[UpgradeDomainsResponse]) extends Message

  final case class UpgradeDomainsResponse(response: Either[UnknownError, Ok]) extends CborSerializable

  final case class UnknownError()

}
