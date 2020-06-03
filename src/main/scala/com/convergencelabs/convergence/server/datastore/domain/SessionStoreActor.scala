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

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import com.convergencelabs.convergence.common.PagedData
import com.convergencelabs.convergence.server.actor.CborSerializable
import com.convergencelabs.convergence.server.datastore.domain.SessionStore.SessionQueryType
import com.convergencelabs.convergence.server.domain.rest.DomainRestActor.DomainRestMessageBody
import grizzled.slf4j.Logging

import scala.util.{Failure, Success}

class SessionStoreActor private[datastore](private[this] val context: ActorContext[SessionStoreActor.Message],
                                           private[this] val sessionStore: SessionStore)
  extends AbstractBehavior[SessionStoreActor.Message](context) with Logging {

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
    val GetSessionsRequest(sessionId, username, remoteHost, authMethod, excludeDisconnected, st, limit, offset, replyTo) = message
    sessionStore.getSessions(sessionId, username, remoteHost, authMethod, excludeDisconnected, st, limit, offset) match {
      case Success(sessions) =>
        replyTo ! GetSessionsSuccess(sessions)
      case Failure(cause) =>
        replyTo ! RequestFailure(cause)
    }
  }

  private[this] def onGetSession(message: GetSessionRequest): Unit = {
    val GetSessionRequest(sessionId, replyTo) = message
    sessionStore.getSession(sessionId) match {
      case Success(session) =>
        replyTo ! GetSessionSuccess(session)
      case Failure(cause) =>
        replyTo ! RequestFailure(cause)
    }
  }
}


object SessionStoreActor {
  def apply(sessionStore: SessionStore): Behavior[Message] = Behaviors.setup { context =>
    new SessionStoreActor(context, sessionStore)
  }

  trait Message extends CborSerializable with DomainRestMessageBody

  case class GetSessionsRequest(sessionId: Option[String],
                                username: Option[String],
                                remoteHost: Option[String],
                                authMethod: Option[String],
                                excludeDisconnected: Boolean,
                                sessionType: SessionQueryType.Value,
                                limit: Option[Int],
                                offset: Option[Int],
                                replyTo: ActorRef[GetSessionsResponse]) extends Message

  sealed trait GetSessionsResponse extends CborSerializable

  case class GetSessionsSuccess(sessions: PagedData[DomainSession]) extends GetSessionsResponse

  case class GetSessionRequest(id: String, replyTo: ActorRef[GetSessionResponse]) extends Message

  sealed trait GetSessionResponse extends CborSerializable

  case class GetSessionSuccess(session: Option[DomainSession]) extends GetSessionResponse

  case class RequestFailure(cause: Throwable) extends CborSerializable
    with GetSessionsResponse
    with GetSessionResponse
}