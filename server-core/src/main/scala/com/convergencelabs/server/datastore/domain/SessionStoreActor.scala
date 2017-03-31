package com.convergencelabs.server.datastore

import com.convergencelabs.server.datastore.domain.SessionStore

import akka.actor.ActorLogging
import akka.actor.Props
import scala.util.Success


object SessionStoreActor {
  def props(sessionStore: SessionStore): Props = Props(new SessionStoreActor(sessionStore))

  trait SessionStoreRequest
  case class GetSessions(limit: Option[Int], offset: Option[Int]) extends SessionStoreRequest
  case class GetSession(id: String) extends SessionStoreRequest
  case class GetConnectedSessions(limit: Option[Int], offset: Option[Int]) extends SessionStoreRequest

}

class SessionStoreActor private[datastore] (private[this] val sessionStore: SessionStore)
    extends StoreActor with ActorLogging {
  import SessionStoreActor._

  def receive: Receive = {
    case message: GetSessions =>
      getSessions(message)
    case message: GetSession =>
      getSession(message)
    case message: GetConnectedSessions =>
      getConnectedSessions(message)
    case message: Any => 
      unhandled(message)
  }

  def getSessions(message: GetSessions): Unit = {
    val GetSessions(limit, offset) = message
    reply(sessionStore.getSessions(limit, offset))
  }

  def getSession(message: GetSession): Unit = {
    val GetSession(sessionId) = message
    reply(sessionStore.getSession(sessionId))
  }

  def getConnectedSessions(message: GetConnectedSessions): Unit = {
    val GetConnectedSessions(limit, offset) = message
    reply(sessionStore.getConnectedSessions(limit, offset))
  }
}
