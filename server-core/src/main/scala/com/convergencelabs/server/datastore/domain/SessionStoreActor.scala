package com.convergencelabs.server.datastore.domain

import com.convergencelabs.server.datastore.StoreActor
import com.convergencelabs.server.datastore.domain.SessionStore.SessionQueryType

import akka.actor.ActorLogging
import akka.actor.Props
import com.convergencelabs.server.domain.DomainUserId
import com.convergencelabs.server.domain.DomainUserType

object SessionStoreActor {
  def props(sessionStore: SessionStore): Props = Props(new SessionStoreActor(sessionStore))

  trait SessionStoreRequest
  case class GetSessions(
    sessionId: Option[String],
    username: Option[String],
    userType: Option[DomainUserType.Value],
    remoteHost: Option[String],
    authMethod: Option[String],
    connectedOnly: Boolean,
    sessionType: SessionQueryType.Value,
    limit: Option[Int],
    offset: Option[Int]) extends SessionStoreRequest
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
    case message: Any =>
      unhandled(message)
  }

  def getSessions(message: GetSessions): Unit = {
    val GetSessions(
      sessionId,
      username,
      userType,
      remoteHost,
      authMethod,
      connectedOnly,
      st,
      limit,
      offset) = message
    reply(sessionStore.getSessions(sessionId,
      username,
      userType,
      remoteHost,
      authMethod,
      connectedOnly,
      st,
      limit,
      offset))
  }

  def getSession(message: GetSession): Unit = {
    val GetSession(sessionId) = message
    reply(sessionStore.getSession(sessionId))
  }
}
