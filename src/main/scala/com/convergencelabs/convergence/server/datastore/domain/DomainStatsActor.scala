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

import akka.actor.{Actor, ActorLogging, Props, Status}
import com.convergencelabs.convergence.server.actor.CborSerializable
import com.convergencelabs.convergence.server.datastore.domain.SessionStore.SessionQueryType
import com.convergencelabs.convergence.server.domain.rest.DomainRestActor.DomainRestMessageBody
import com.convergencelabs.convergence.server.util.concurrent.UnexpectedErrorException

import scala.util.Try

class DomainStatsActor(persistence: DomainPersistenceProvider) extends Actor with ActorLogging {

  import DomainStatsActor._

  def receive: Receive = {
    case GetStatsRequest =>
      onGetStats()
    case message: Any =>
      unhandled(message)
  }

  private[this] def onGetStats(): Unit = {
    (for {
      sessionCount <- persistence.sessionStore.getConnectedSessionsCount(SessionQueryType.ExcludeConvergence)
      userCount <- persistence.userStore.getNormalUserCount()
      modelCount <- persistence.modelStore.getModelCount()
      dbSize <- databaseSize()
    } yield {
      sender ! GetDomainStatsResponse(DomainStats(sessionCount, userCount, modelCount, dbSize))
    }) recover {
      case cause: Exception =>
        sender ! Status.Failure(new UnexpectedErrorException("Unexpected error getting domain stats"))
    }
  }

  private[this] def databaseSize(): Try[Long] = persistence.dbProvider.tryWithDatabase { db =>
    db.getSize()
  }
}


object DomainStatsActor {
  def props(persistence: DomainPersistenceProvider): Props =
    Props(new DomainStatsActor(persistence))

  sealed trait DomainStatsRequest extends CborSerializable with DomainRestMessageBody

  case object GetStatsRequest extends DomainStatsRequest

  case class GetDomainStatsResponse(stats: DomainStats) extends CborSerializable

  case class DomainStats(connectedSessions: Long, users: Long, models: Long, dbSize: Long)

}
