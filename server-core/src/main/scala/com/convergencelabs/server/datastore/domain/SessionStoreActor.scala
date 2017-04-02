package com.convergencelabs.server.datastore

import com.convergencelabs.server.datastore.domain.SessionStore

import akka.actor.ActorLogging
import akka.actor.Props
import scala.util.Success
import com.convergencelabs.server.datastore.domain.SessionStore.SessionQueryType


object SessionStoreActor {
  def props(sessionStore: SessionStore): Props = Props(new SessionStoreActor(sessionStore))

  trait SessionStoreRequest
  case class GetSessions(limit: Option[Int], offset: Option[Int], sessionType: SessionQueryType.Value) extends SessionStoreRequest
  case class GetConnectedSessions(limit: Option[Int], offset: Option[Int], sessionType: SessionQueryType.Value) extends SessionStoreRequest
  case class GetSession(id: String) extends SessionStoreRequest
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
    val GetSessions(limit, offset, sessionType) = message
    reply(sessionStore.getSessions(limit, offset, sessionType))
  }

  def getSession(message: GetSession): Unit = {
    val GetSession(sessionId) = message
    reply(sessionStore.getSession(sessionId))
  }

  def getConnectedSessions(message: GetConnectedSessions): Unit = {
    val GetConnectedSessions(limit, offset, sessionType) = message
    reply(sessionStore.getConnectedSessions(limit, offset, sessionType))
  }
}
