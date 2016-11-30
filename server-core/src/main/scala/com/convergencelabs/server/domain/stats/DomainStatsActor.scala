package com.convergencelabs.server.domain.stats

import scala.util.Try

import com.convergencelabs.server.datastore.domain.DomainPersistenceProvider
import com.convergencelabs.server.domain.stats.DomainStatsActor.DomainStats
import com.convergencelabs.server.domain.stats.DomainStatsActor.GetStats
import com.convergencelabs.server.util.TryWithResource

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

  def receive: Receive = {
    case GetStats => getStats()
    case message: Any => unhandled(message)
  }

  def getStats(): Unit = {
    val foo = for {
      sessionCount <- persistence.sessionStore.getConnectedSessionsCount()
      userCount <- persistence.userStore.getNormalUserCount()
      dbSize <- getDatabaseSize()
    } yield {
      sender ! DomainStats(sessionCount, userCount, dbSize)
    }
    
    foo recover {
      case cause: Exception =>
        sender ! akka.actor.Status.Failure(cause)
    }
  }

  def getDatabaseSize(): Try[Long] = TryWithResource(persistence.dbPool.acquire()) { db =>
    db.getSize()
  }
}
