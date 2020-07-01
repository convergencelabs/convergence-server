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

package com.convergencelabs.convergence.server.backend.services.domain.stats

import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import com.convergencelabs.convergence.server.backend.datastore.domain.DomainPersistenceProvider
import com.convergencelabs.convergence.server.backend.datastore.domain.session.SessionQueryType
import com.convergencelabs.convergence.server.util.serialization.akka.CborSerializable
import com.fasterxml.jackson.annotation.{JsonSubTypes, JsonTypeInfo}

import scala.annotation.nowarn
import scala.util.Try

private final class DomainStatsActor(context: ActorContext[DomainStatsActor.Message],
                                     persistence: DomainPersistenceProvider)
  extends AbstractBehavior[DomainStatsActor.Message](context) {

  import DomainStatsActor._

  override def onMessage(msg: Message): Behavior[Message] = {
    msg match {
      case msg: GetStatsRequest =>
        onGetStats(msg)
    }

    Behaviors.same
  }

  private[this] def onGetStats(msg: GetStatsRequest): Unit = {
    val GetStatsRequest(replyTo) = msg
    (for {
      sessionCount <- persistence.sessionStore.getConnectedSessionsCount(SessionQueryType.ExcludeConvergence)
      userCount <- persistence.userStore.getNormalUserCount()
      modelCount <- persistence.modelStore.getModelCount()
      dbSize <- databaseSize()
    } yield {
      DomainStats(sessionCount, userCount, modelCount, dbSize)
    })
      .map(s => GetStatsResponse(Right(s)))
      .recover { cause =>
        context.log.error("Unexpected error getting domain stats", cause)
        GetStatsResponse(Left(UnknownError()))
      }
      .foreach(replyTo ! _)
  }

  @nowarn
  private[this] def databaseSize(): Try[Long] = persistence.dbProvider.tryWithDatabase { db =>
    db.getSize
  }
}


object DomainStatsActor {
  def apply(persistence: DomainPersistenceProvider): Behavior[Message] =
    Behaviors.setup(context => new DomainStatsActor(context, persistence))


  /////////////////////////////////////////////////////////////////////////////
  // Message Protocol
  /////////////////////////////////////////////////////////////////////////////

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[GetStatsRequest], name = "get_stats")
  ))
  sealed trait Message extends CborSerializable

  //
  // GetStats
  //
  final case class GetStatsRequest(replyTo: ActorRef[GetStatsResponse]) extends Message

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[UnknownError], name = "unknown")
  ))
  sealed trait GetStatsError

  final case class GetStatsResponse(stats: Either[GetStatsError, DomainStats]) extends CborSerializable

  final case class DomainStats(connectedSessions: Long, users: Long, models: Long, dbSize: Long)

  //
  // Common Errors
  //

  final case class UnknownError() extends AnyRef
    with GetStatsError

}
