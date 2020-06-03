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

package com.convergencelabs.convergence.server.datastore.domain

import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import com.convergencelabs.convergence.server.actor.CborSerializable
import com.convergencelabs.convergence.server.datastore.domain.SessionStore.SessionQueryType
import com.convergencelabs.convergence.server.domain.rest.DomainRestActor.DomainRestMessageBody
import com.convergencelabs.convergence.server.util.concurrent.UnexpectedErrorException
import grizzled.slf4j.Logging

import scala.util.{Failure, Success, Try}

class DomainStatsActor(private[this] val context: ActorContext[DomainStatsActor.Message],
                       private[this] val persistence: DomainPersistenceProvider)
  extends AbstractBehavior[DomainStatsActor.Message](context) with Logging {

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
      GetStatsSuccess(DomainStats(sessionCount, userCount, modelCount, dbSize))
    }) match {
      case Success(response) =>
        replyTo ! response
      case Failure(_) =>
        replyTo ! RequestFailure(UnexpectedErrorException("Unexpected error getting domain stats"))
    }
  }

  private[this] def databaseSize(): Try[Long] = persistence.dbProvider.tryWithDatabase { db =>

    db.getSize()
  }
}


object DomainStatsActor {
  def apply(persistence: DomainPersistenceProvider): Behavior[Message] = Behaviors.setup { context =>
    new DomainStatsActor(context, persistence)
  }

  sealed trait Message extends CborSerializable with DomainRestMessageBody

  case class GetStatsRequest(replyTo: ActorRef[GetStatsResponse]) extends Message

  sealed trait GetStatsResponse extends CborSerializable

  case class GetStatsSuccess(stats: DomainStats) extends GetStatsResponse

  case class DomainStats(connectedSessions: Long, users: Long, models: Long, dbSize: Long)

  case class RequestFailure(cause: Throwable) extends CborSerializable
    with GetStatsResponse

}
