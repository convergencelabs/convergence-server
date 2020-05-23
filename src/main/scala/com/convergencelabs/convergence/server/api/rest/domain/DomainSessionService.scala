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

package com.convergencelabs.convergence.server.api.rest.domain

import java.time.Instant

import akka.actor.ActorRef
import akka.http.scaladsl.marshalling.ToResponseMarshallable.apply
import akka.http.scaladsl.server.Directive.{addByNameNullaryApply, addDirectiveApply}
import akka.http.scaladsl.server.Directives.{Segment, _enhanceRouteWithConcatenation, _segmentStringToPathMatcher, _string2NR, complete, get, parameters, pathEnd, pathPrefix}
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import akka.util.Timeout
import com.convergencelabs.convergence.server.api.rest.{RestResponse, notFoundResponse, okResponse}
import com.convergencelabs.convergence.server.datastore.domain.DomainSession
import com.convergencelabs.convergence.server.datastore.domain.SessionStore.SessionQueryType
import com.convergencelabs.convergence.server.datastore.domain.SessionStoreActor.{GetSessionRequest, GetSessionResponse, GetSessionsRequest, GetSessionsResponse}
import com.convergencelabs.convergence.server.domain.DomainId
import com.convergencelabs.convergence.server.domain.rest.DomainRestActor.DomainRestMessage
import com.convergencelabs.convergence.server.security.AuthorizationProfile

import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps

object DomainSessionService {

  case class DomainSessionData(id: String,
                               username: String,
                               userType: String,
                               connected: Instant,
                               disconnected: Option[Instant],
                               authMethod: String,
                               client: String,
                               clientVersion: String,
                               clientMetaData: String,
                               remoteHost: String)

}

class DomainSessionService(private[this] val executionContext: ExecutionContext,
                           private[this] val timeout: Timeout,
                           private[this] val domainRestActor: ActorRef)
  extends AbstractDomainRestService(executionContext, timeout) {

  import DomainSessionService._

  def route(authProfile: AuthorizationProfile, domain: DomainId): Route = {
    pathPrefix("sessions") {
      pathEnd {
        get {
          parameters(
            "sessionId".as[String].?,
            "username".as[String].?,
            "remoteHost".as[String].?,
            "authMethod".as[String].?,
            "excludeDisconnected".as[Boolean] ?,
            "sessionType".as[String] ?,
            "limit".as[Int].?,
            "offset".as[Int].?) { (sessionId, sessionUsername, remoteHost, authMethod, excludeDisconnected, sessionType, limit, offset) => {
            complete(getSessions(
              domain,
              sessionId,
              sessionUsername,
              remoteHost,
              authMethod,
              excludeDisconnected,
              sessionType,
              limit,
              offset))
          }
          }
        }
      } ~ pathPrefix(Segment) { sessionId =>
        pathEnd {
          get {
            complete(getSession(domain, sessionId))
          }
        }
      }
    }
  }

  private[this] def getSessions(domain: DomainId,
                                sessionId: Option[String],
                                username: Option[String],
                                remoteHost: Option[String],
                                authMethod: Option[String],
                                excludeDisconnected: Option[Boolean],
                                sessionType: Option[String],
                                limit: Option[Int],
                                offset: Option[Int]): Future[RestResponse] = {

    val st = sessionType
      .flatMap(t => SessionQueryType.withNameOpt(t))
      .getOrElse(SessionQueryType.All)

    val getMessage = GetSessionsRequest(
      sessionId,
      username,
      remoteHost,
      authMethod,
      excludeDisconnected.getOrElse(false),
      st,
      limit,
      offset)
    val message = DomainRestMessage(domain, getMessage)
    (domainRestActor ? message)
      .mapTo[GetSessionsResponse]
      .map(_.sessions)
      .map(sessions =>
        okResponse(sessions.map(sessionToSessionData)))
  }

  private[this] def getSession(domain: DomainId, sessionId: String): Future[RestResponse] = {
    val message = DomainRestMessage(domain, GetSessionRequest(sessionId))
    (domainRestActor ? message)
      .mapTo[GetSessionResponse]
      .map(_.session)
      .map {
        case Some(sessions) => okResponse(sessionToSessionData(sessions))
        case None => notFoundResponse()
      }
  }

  private[this] def sessionToSessionData(session: DomainSession): DomainSessionData = {
    val DomainSession(
    id,
    userId,
    connected,
    disconnected,
    authMethod,
    client,
    clientVersion,
    clientMetaData,
    remoteHost) = session

    DomainSessionData(
      id,
      userId.username,
      userId.userType.toString.toLowerCase,
      connected,
      disconnected,
      authMethod,
      client,
      clientVersion,
      clientMetaData,
      remoteHost)
  }
}
