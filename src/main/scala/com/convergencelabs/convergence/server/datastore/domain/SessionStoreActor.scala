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

import akka.actor.{ActorLogging, Props}
import com.convergencelabs.convergence.server.actor.CborSerializable
import com.convergencelabs.convergence.server.datastore.StoreActor
import com.convergencelabs.convergence.server.datastore.domain.SessionStore.SessionQueryType

class SessionStoreActor private[datastore] (private[this] val sessionStore: SessionStore)
    extends StoreActor with ActorLogging {
  import SessionStoreActor._

  def receive: Receive = {
    case message: GetSessionsRequest =>
      onGetSessions(message)
    case message: GetSessionRequest =>
      onGetSession(message)
    case message: Any =>
      unhandled(message)
  }

  private[this] def onGetSessions(message: GetSessionsRequest): Unit = {
    val GetSessionsRequest(
      sessionId,
      username,
      remoteHost,
      authMethod,
      excludeDisconnected,
      st,
      limit,
      offset) = message
    reply(sessionStore.getSessions(sessionId,
      username,
      remoteHost,
      authMethod,
      excludeDisconnected,
      st,
      limit,
      offset).map(GetSessionsResponse))
  }

  private[this] def onGetSession(message: GetSessionRequest): Unit = {
    val GetSessionRequest(sessionId) = message
    reply(sessionStore.getSession(sessionId).map(GetSessionResponse))
  }
}


object SessionStoreActor {
  def props(sessionStore: SessionStore): Props = Props(new SessionStoreActor(sessionStore))

  trait SessionStoreRequest extends CborSerializable
  case class GetSessionsRequest(
                                 sessionId: Option[String],
                                 username: Option[String],
                                 remoteHost: Option[String],
                                 authMethod: Option[String],
                                 excludeDisconnected: Boolean,
                                 sessionType: SessionQueryType.Value,
                                 limit: Option[Int],
                                 offset: Option[Int]) extends SessionStoreRequest
  case class GetSessionsResponse(sessions: List[DomainSession]) extends CborSerializable

  case class GetSessionRequest(id: String) extends SessionStoreRequest
  case class GetSessionResponse(session: Option[DomainSession]) extends CborSerializable
}