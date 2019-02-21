package com.convergencelabs.server.frontend.rest

import java.time.Instant

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.language.postfixOps

import com.convergencelabs.server.datastore.domain.DomainSession
import com.convergencelabs.server.datastore.domain.SessionStore.SessionQueryType
import com.convergencelabs.server.datastore.domain.SessionStoreActor.GetSession
import com.convergencelabs.server.datastore.domain.SessionStoreActor.GetSessions
import com.convergencelabs.server.domain.DomainFqn
import com.convergencelabs.server.domain.DomainUserType
import com.convergencelabs.server.domain.rest.RestDomainActor.DomainRestMessage
import com.convergencelabs.server.security.AuthorizationProfile

import akka.actor.ActorRef
import akka.http.scaladsl.marshalling.ToResponseMarshallable.apply
import akka.http.scaladsl.server.Directive.addByNameNullaryApply
import akka.http.scaladsl.server.Directive.addDirectiveApply
import akka.http.scaladsl.server.Directives._enhanceRouteWithConcatenation
import akka.http.scaladsl.server.Directives._segmentStringToPathMatcher
import akka.http.scaladsl.server.Directives._string2NR
import akka.http.scaladsl.server.Directives.authorize
import akka.http.scaladsl.server.Directives.complete
import akka.http.scaladsl.server.Directives.get
import akka.http.scaladsl.server.Directives.parameters
import akka.http.scaladsl.server.Directives.pathEnd
import akka.http.scaladsl.server.Directives.pathPrefix
import akka.http.scaladsl.server.Route
import akka.util.Timeout

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

  def route(authProfile: AuthorizationProfile, domain: DomainFqn): Route = {
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
    domain: DomainFqn,
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
      okResponse(sessions.map(sessionToSessionData(_))))
  }

  def getSession(domain: DomainFqn, sessionId: String): Future[RestResponse] = {
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
