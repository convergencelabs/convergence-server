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

package com.convergencelabs.convergence.server.backend.services.domain.session

import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import com.convergencelabs.convergence.common.PagedData
import com.convergencelabs.convergence.server.actor.CborSerializable
import com.convergencelabs.convergence.server.backend.datastore.domain.session.SessionStore
import com.convergencelabs.convergence.server.model.domain.session.DomainSession
import com.convergencelabs.convergence.server.util.{QueryLimit, QueryOffset}
import com.fasterxml.jackson.annotation.{JsonSubTypes, JsonTypeInfo}
import com.fasterxml.jackson.databind.annotation.JsonDeserialize

class SessionStoreActor private(context: ActorContext[SessionStoreActor.Message],
                                sessionStore: SessionStore)
  extends AbstractBehavior[SessionStoreActor.Message](context) {

  import SessionStoreActor._

  override def onMessage(msg: Message): Behavior[Message] = {
    msg match {
      case message: GetSessionsRequest =>
        onGetSessions(message)
      case message: GetSessionRequest =>
        onGetSession(message)
    }

    Behaviors.same
  }

  private[this] def onGetSessions(message: GetSessionsRequest): Unit = {
    val GetSessionsRequest(sessionId, username, remoteHost, authMethod, excludeDisconnected, st, offset, limit, replyTo) = message
    sessionStore
      .getSessions(sessionId, username, remoteHost, authMethod, excludeDisconnected, st, offset, limit)
      .map(sessions => GetSessionsResponse(Right(sessions)))
      .recover { _ =>
        GetSessionsResponse(Left(UnknownError()))
      }
      .foreach(replyTo ! _)
  }

  private[this] def onGetSession(message: GetSessionRequest): Unit = {
    val GetSessionRequest(sessionId, replyTo) = message
    sessionStore
      .getSession(sessionId)
      .map(_.map(s => GetSessionResponse(Right(s))).getOrElse(GetSessionResponse(Left(SessionNotFoundError()))))
      .recover { _ =>
        GetSessionResponse(Left(UnknownError()))
      }
      .foreach(replyTo ! _)
  }
}

object SessionStoreActor {
  def apply(sessionStore: SessionStore): Behavior[Message] =
    Behaviors.setup(context => new SessionStoreActor(context, sessionStore))

  /////////////////////////////////////////////////////////////////////////////
  // Message Protocol
  /////////////////////////////////////////////////////////////////////////////


  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[GetSessionRequest], name = "get_session"),
    new JsonSubTypes.Type(value = classOf[GetSessionsRequest], name = "get_sessions")
  ))
  sealed trait Message extends CborSerializable

  //
  // GetSessions
  //
  final case class GetSessionsRequest(sessionId: Option[String],
                                      username: Option[String],
                                      remoteHost: Option[String],
                                      authMethod: Option[String],
                                      excludeDisconnected: Boolean,
                                      sessionType: SessionQueryType.Value,
                                      @JsonDeserialize(contentAs = classOf[Long])
                                      offset: QueryOffset,
                                      @JsonDeserialize(contentAs = classOf[Long])
                                      limit: QueryLimit,
                                      replyTo: ActorRef[GetSessionsResponse]) extends Message

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[UnknownError], name = "unknown")
  ))
  sealed trait GetSessionsError

  final case class GetSessionsResponse(sessions: Either[GetSessionsError, PagedData[DomainSession]]) extends CborSerializable

  //
  // GetSession
  //
  final case class GetSessionRequest(id: String, replyTo: ActorRef[GetSessionResponse]) extends Message

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[SessionNotFoundError], name = "not_found"),
    new JsonSubTypes.Type(value = classOf[UnknownError], name = "unknown")
  ))
  sealed trait GetSessionError

  final case class SessionNotFoundError() extends GetSessionError

  final case class GetSessionResponse(session: Either[GetSessionError, DomainSession]) extends CborSerializable

  //
  // Common Errors
  //
  final case class UnknownError() extends AnyRef
    with GetSessionsError
    with GetSessionError

}