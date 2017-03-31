package com.convergencelabs.server.frontend.rest

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import com.convergencelabs.server.domain.DomainFqn
import com.convergencelabs.server.domain.RestDomainManagerActor.DomainMessage

import akka.actor.ActorRef
import akka.http.scaladsl.marshalling.ToResponseMarshallable.apply
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directive.addByNameNullaryApply
import akka.http.scaladsl.server.Directive.addDirectiveApply
import akka.http.scaladsl.server.Directives.authorizeAsync
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import akka.util.Timeout
import com.convergencelabs.server.domain.AuthorizationActor.ConvergenceAuthorizedRequest
import scala.util.Try
import com.convergencelabs.server.frontend.rest.DomainConfigService.ModelSnapshotPolicyData
import com.convergencelabs.server.domain.ModelSnapshotConfig
import java.time.Duration
import com.convergencelabs.server.datastore.domain.CollectionPermissions
import com.convergencelabs.server.datastore.SessionStoreActor.GetConnectedSessions
import com.convergencelabs.server.datastore.SessionStoreActor.GetSessions
import com.convergencelabs.server.datastore.domain.DomainSession
import com.convergencelabs.server.frontend.rest.DomainSessionService.GetSessionsResponse
import com.convergencelabs.server.datastore.SessionStoreActor.GetSession
import java.time.Instant
import com.convergencelabs.server.frontend.rest.DomainSessionService.DomainSessionData
import com.convergencelabs.server.frontend.rest.DomainSessionService.GetSessionResponse

object DomainSessionService {
  case class GetSessionsResponse(sessions: List[DomainSessionData]) extends AbstractSuccessResponse
  case class GetSessionResponse(session: DomainSessionData) extends AbstractSuccessResponse
  case class DomainSessionData(
    id: String,
    username: String,
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
  private[this] val authorizationActor: ActorRef,
  private[this] val domainRestActor: ActorRef,
  private[this] val defaultTimeout: Timeout)
    extends JsonSupport {

  implicit val ec = executionContext
  implicit val t = defaultTimeout

  def route(username: String, domain: DomainFqn): Route = {
    pathPrefix("sessions") {
      pathEnd {
        get {
          parameters("connected".as[Boolean]?, "limit".as[Int].?, "offset".as[Int].?) { (connected, limit, offset) =>
            {
              authorizeAsync(canAccessDomain(domain, username)) {
                complete(getSessions(domain, connected, limit, offset))
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

  def getSessions(domain: DomainFqn, connected: Option[Boolean], limit: Option[Int], offset: Option[Int]): Future[RestResponse] = {
    val message = connected.getOrElse(false) match {
      case true => 
        DomainMessage(domain, GetConnectedSessions(limit, offset))
      case false => 
        DomainMessage(domain, GetSessions(limit, offset))
    }
    println(message)
    (domainRestActor ? message).mapTo[List[DomainSession]] map (sessions =>
      (StatusCodes.OK, GetSessionsResponse(sessions.map(sessionToSessionData(_)))))
  }

  def getSession(domain: DomainFqn, sessionId: String): Future[RestResponse] = {
    val message = DomainMessage(domain, GetSession(sessionId))
    (domainRestActor ? message).mapTo[Option[DomainSession]] map {
      case Some(collection) => (StatusCodes.OK, GetSessionResponse(sessionToSessionData(collection)))
      case None => NotFoundError
    }
  }

  def sessionToSessionData(session: DomainSession): DomainSessionData = {
    val DomainSession(
      id,
      username,
      connected,
      disconnected,
      authMethod,
      client,
      clientVersion,
      clientMetaData,
      remoteHost) = session

    DomainSessionData(
      id,
      username,
      connected,
      disconnected,
      authMethod,
      client,
      clientVersion,
      clientMetaData,
      remoteHost)
  }

  // Permission Checks

  def canAccessDomain(domainFqn: DomainFqn, username: String): Future[Boolean] = {
    (authorizationActor ? ConvergenceAuthorizedRequest(username, domainFqn, Set("domain-access"))).mapTo[Try[Boolean]].map(_.get)
  }
}
