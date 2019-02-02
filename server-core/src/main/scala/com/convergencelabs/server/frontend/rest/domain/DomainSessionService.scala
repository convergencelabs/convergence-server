package com.convergencelabs.server.frontend.rest

import java.time.Instant

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.language.postfixOps
import scala.util.Try

import com.convergencelabs.server.datastore.domain.DomainSession
import com.convergencelabs.server.datastore.domain.SessionStore.SessionQueryType
import com.convergencelabs.server.domain.DomainFqn
import com.convergencelabs.server.frontend.rest.DomainSessionService.DomainSessionData
import com.convergencelabs.server.frontend.rest.DomainSessionService.GetSessionResponse
import com.convergencelabs.server.frontend.rest.DomainSessionService.GetSessionsResponse
import com.convergencelabs.server.domain.rest.RestDomainActor.DomainRestMessage
import com.convergencelabs.server.domain.rest.AuthorizationActor.ConvergenceAuthorizedRequest
import com.convergencelabs.server.datastore.domain.SessionStoreActor.GetSession
import com.convergencelabs.server.datastore.domain.SessionStoreActor.GetSessions

import akka.actor.ActorRef
import akka.http.scaladsl.marshalling.ToResponseMarshallable.apply
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directive.addByNameNullaryApply
import akka.http.scaladsl.server.Directive.addDirectiveApply
import akka.http.scaladsl.server.Directives.Segment
import akka.http.scaladsl.server.Directives._enhanceRouteWithConcatenation
import akka.http.scaladsl.server.Directives._segmentStringToPathMatcher
import akka.http.scaladsl.server.Directives._string2NR
import akka.http.scaladsl.server.Directives.authorizeAsync
import akka.http.scaladsl.server.Directives.complete
import akka.http.scaladsl.server.Directives.get
import akka.http.scaladsl.server.Directives.parameters
import akka.http.scaladsl.server.Directives.pathEnd
import akka.http.scaladsl.server.Directives.pathPrefix
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import akka.pattern.ask
import com.convergencelabs.server.domain.DomainUserType
import com.convergencelabs.server.domain.DomainUserType.DomainUserType

object DomainSessionService {
  case class GetSessionsResponse(sessions: List[DomainSessionData])
  case class GetSessionResponse(session: DomainSessionData)
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
  private[this] val authActor: ActorRef,
  private[this] val domainRestActor: ActorRef)
    extends DomainRestService(executionContext, timeout, authActor) {

  def route(username: String, domain: DomainFqn): Route = {
    pathPrefix("sessions") {
      pathEnd {
        get {
          parameters(
            "sessionId".as[String].?,
            "username".as[String].?,
            "userType".as[String].?,
            "remoteHost".as[String].?,
            "authMethod".as[String].?,
            "connectedOnly".as[Boolean]?,
            "sessionType".as[String]?,
            "limit".as[Int].?,
            "offset".as[Int].?) { (sessionId, sessionUsername, userType, remoteHost, authMethod, connectedOnly, sessionType, limit, offset) =>
              {
                authorizeAsync(canAccessDomain(domain, username)) {
                  complete(getSessions(
                    domain,
                    sessionId,
                    sessionUsername,
                    userType,
                    remoteHost,
                    authMethod,
                    connectedOnly,
                    sessionType,
                    limit,
                    offset))
                }
              }
            }
        }
      } ~ pathPrefix(Segment) { sessionId =>
        pathEnd {
          get {
            authorizeAsync(canAccessDomain(domain, username)) {
              complete(getSession(domain, sessionId))
            }
          }
        }
      }
    }
  }

  def getSessions(
    domain: DomainFqn,
    sessionId: Option[String],
    username: Option[String],
    userType: Option[String],
    remoteHost: Option[String],
    authMethod: Option[String],
    connectedOnly: Option[Boolean],
    sessionType: Option[String],
    limit: Option[Int],
    offset: Option[Int]): Future[RestResponse] = {

    val st = sessionType
      .flatMap(t => SessionQueryType.withNameOpt(t))
      .getOrElse(SessionQueryType.All)
      
    val getMessage = GetSessions(
      sessionId,
      username,
      userType.flatMap(DomainUserType.withNameOpt(_)),
      remoteHost,
      authMethod,
      connectedOnly.getOrElse(false),
      st,
      limit,
      offset)
    val message = DomainRestMessage(domain, getMessage)
    (domainRestActor ? message).mapTo[List[DomainSession]] map (sessions =>
      okResponse(GetSessionsResponse(sessions.map(sessionToSessionData(_)))))
  }

  def getSession(domain: DomainFqn, sessionId: String): Future[RestResponse] = {
    val message = DomainRestMessage(domain, GetSession(sessionId))
    (domainRestActor ? message).mapTo[Option[DomainSession]] map {
      case Some(sessions) => okResponse(GetSessionResponse(sessionToSessionData(sessions)))
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
