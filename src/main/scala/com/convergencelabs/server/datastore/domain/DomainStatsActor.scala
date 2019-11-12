/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

package com.convergencelabs.server.datastore.domain

import scala.util.Try

import com.convergencelabs.server.datastore.domain.SessionStore.SessionQueryType
import com.convergencelabs.server.util.concurrent.UnexpectedErrorException

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
