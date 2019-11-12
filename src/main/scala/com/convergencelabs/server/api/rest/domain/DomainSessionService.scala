/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

package com.convergencelabs.server.api.rest.domain

import java.time.Instant

import akka.actor.ActorRef
import akka.http.scaladsl.marshalling.ToResponseMarshallable.apply
import akka.http.scaladsl.server.Directive.{addByNameNullaryApply, addDirectiveApply}
import akka.http.scaladsl.server.Directives.{_enhanceRouteWithConcatenation, _segmentStringToPathMatcher, _string2NR, complete, get, parameters, pathEnd, pathPrefix}
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import com.convergencelabs.server.api.rest.{RestResponse, notFoundResponse, okResponse}
import com.convergencelabs.server.datastore.domain.DomainSession
import com.convergencelabs.server.datastore.domain.SessionStore.SessionQueryType
import com.convergencelabs.server.datastore.domain.SessionStoreActor.{GetSession, GetSessions}
import com.convergencelabs.server.domain.DomainId
import com.convergencelabs.server.domain.rest.RestDomainActor.DomainRestMessage
import com.convergencelabs.server.security.AuthorizationProfile

import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps

object DomainSessionService {
  case class DomainSessionData(
    id: String,
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

class DomainSessionService(
  private[this] val executionContext: ExecutionContext,
  private[this] val timeout: Timeout,
  private[this] val domainRestActor: ActorRef)
  extends DomainRestService(executionContext, timeout) {

  import DomainSessionService._
  import akka.http.scaladsl.server.Directives.Segment
  import akka.pattern.ask

  def route(authProfile: AuthorizationProfile, domain: DomainId): Route = {
    pathPrefix("sessions") {
      pathEnd {
        get {
          parameters(
            "sessionId".as[String].?,
            "username".as[String].?,
            "remoteHost".as[String].?,
            "authMethod".as[String].?,
            "excludeDisconnected".as[Boolean]?,
            "sessionType".as[String]?,
            "limit".as[Int].?,
            "offset".as[Int].?) { (sessionId, sessionUsername, remoteHost, authMethod, excludeDisconnected, sessionType, limit, offset) =>
              {
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

  def getSessions(
    domain: DomainId,
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

    val getMessage = GetSessions(
      sessionId,
      username,
      remoteHost,
      authMethod,
      excludeDisconnected.getOrElse(false),
      st,
      limit,
      offset)
    val message = DomainRestMessage(domain, getMessage)
    (domainRestActor ? message).mapTo[List[DomainSession]] map (sessions =>
      okResponse(sessions.map(sessionToSessionData)))
  }

  def getSession(domain: DomainId, sessionId: String): Future[RestResponse] = {
    val message = DomainRestMessage(domain, GetSession(sessionId))
    (domainRestActor ? message).mapTo[Option[DomainSession]] map {
      case Some(sessions) => okResponse(sessionToSessionData(sessions))
      case None => notFoundResponse()
    }
  }

  def sessionToSessionData(session: DomainSession): DomainSessionData = {
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
