package com.convergencelabs.server.datastore.domain

import scala.util.Try

import com.convergencelabs.server.datastore.domain.SessionStore.SessionQueryType

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.Props
import akka.actor.actorRef2Scala

object DomainStatsActor {
  def props(persistence: DomainPersistenceProvider): Props =
    Props(new DomainStatsActor(persistence))

  trait DomainStatsRequest
  case object GetStats extends DomainStatsRequest

  case class DomainStats(connectedSessions: Long, users: Long, dbSize: Long)
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
      sessionCount <- persistence.sessionStore.getConnectedSessionsCount(SessionQueryType.NonAdmin)
      userCount <- persistence.userStore.getNormalUserCount()
      dbSize <- getDatabaseSize()
    } yield {
      sender ! DomainStats(sessionCount, userCount, dbSize)
    }) recover {
      case cause: Exception =>
        sender ! akka.actor.Status.Failure(cause)
    }
  }

  def getDatabaseSize(): Try[Long] = persistence.dbProvider.tryWithDatabase { db =>
    db.getSize()
  }
}
