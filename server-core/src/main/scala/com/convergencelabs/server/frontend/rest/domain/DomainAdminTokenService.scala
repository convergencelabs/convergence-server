package com.convergencelabs.server.frontend.rest.domain

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Try

import com.convergencelabs.server.domain.DomainFqn
import com.convergencelabs.server.domain.rest.AuthorizationActor.ConvergenceAuthorizedRequest
import com.convergencelabs.server.domain.rest.RestDomainActor.AdminTokenRequest
import com.convergencelabs.server.domain.rest.RestDomainActor.DomainRestMessage
import com.convergencelabs.server.frontend.rest.AbstractSuccessResponse
import com.convergencelabs.server.frontend.rest.JsonSupport
import com.convergencelabs.server.frontend.rest.RestResponse

import akka.actor.ActorRef
import akka.http.scaladsl.marshalling.ToResponseMarshallable.apply
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directive.addByNameNullaryApply
import akka.http.scaladsl.server.Directives._segmentStringToPathMatcher
import akka.http.scaladsl.server.Directives.authorizeAsync
import akka.http.scaladsl.server.Directives.complete
import akka.http.scaladsl.server.Directives.get
import akka.http.scaladsl.server.Directives.pathEnd
import akka.http.scaladsl.server.Directives.pathPrefix
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import com.convergencelabs.server.frontend.rest.DomainRestService

object DomainAdminTokenService {
  case class AdminTokenRestResponse(token: String) extends AbstractSuccessResponse
}

class DomainAdminTokenService(
  executionContext: ExecutionContext,
  timeout: Timeout,
  authActor: ActorRef,
  private[this] val domainRestActor: ActorRef)
    extends DomainRestService(executionContext, timeout, authActor) {

  import DomainAdminTokenService._
  import akka.pattern.ask
  
  def route(username: String, domain: DomainFqn): Route = {
    pathPrefix("adminToken") {
      pathEnd {
        get {
          authorizeAsync(canAccessDomain(domain, username)) {
            complete(getAdminToken(domain, username))
          }
        }
      }
    }
  }

  def getAdminToken(domain: DomainFqn, username: String): Future[RestResponse] = {
    val message = DomainRestMessage(domain, AdminTokenRequest(username))
    (domainRestActor ? message).mapTo[String] map {
      case token: String => (StatusCodes.OK, AdminTokenRestResponse(token))
    }
  }
}
