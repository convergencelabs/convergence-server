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

import scala.util.Try

import com.convergencelabs.convergence.server.datastore.domain.SessionStore.SessionQueryType
import com.convergencelabs.convergence.server.util.concurrent.UnexpectedErrorException

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.Props
import akka.actor.Status

object DomainStatsActor {
  def props(persistence: DomainPersistenceProvider): Props =
    Props(new DomainStatsActor(persistence))

  trait DomainStatsRequest
  case object GetStats extends DomainStatsRequest

  case class DomainStats(connectedSessions: Long, users: Long, models: Long, dbSize: Long)
}

class DomainStatsActor(
    persistence: DomainPersistenceProvider) extends Actor with ActorLogging {

  import DomainStatsActor._
  
  def receive: Receive = {
    case GetStats => getStats()
    case message: Any => unhandled(message)
  }

  def getStats(): Unit = {
    (for {
      sessionCount <- persistence.sessionStore.getConnectedSessionsCount(SessionQueryType.ExcludeConvergence)
      userCount <- persistence.userStore.getNormalUserCount()
      modelCount <- persistence.modelStore.getModelCount()
      dbSize <- getDatabaseSize()
    } yield {
      sender ! DomainStats(sessionCount, userCount, modelCount, dbSize)
    }) recover {
      case cause: Exception =>
        sender ! Status.Failure(new UnexpectedErrorException("Unexpected error getting domain stats"))
    }
  }

  def getDatabaseSize(): Try[Long] = persistence.dbProvider.tryWithDatabase { db =>
    db.getSize()
  }
}
