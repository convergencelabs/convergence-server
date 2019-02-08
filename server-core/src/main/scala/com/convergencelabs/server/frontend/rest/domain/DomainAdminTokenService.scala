package com.convergencelabs.server.frontend.rest.domain

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import com.convergencelabs.server.domain.DomainFqn
import com.convergencelabs.server.domain.rest.RestDomainActor.AdminTokenRequest
import com.convergencelabs.server.domain.rest.RestDomainActor.DomainRestMessage
import com.convergencelabs.server.frontend.rest.DomainRestService
import com.convergencelabs.server.frontend.rest.RestResponse
import com.convergencelabs.server.frontend.rest.okResponse

import akka.actor.ActorRef
import akka.http.scaladsl.marshalling.ToResponseMarshallable.apply
import akka.http.scaladsl.server.Directive.addByNameNullaryApply
import akka.http.scaladsl.server.Directives._segmentStringToPathMatcher
import akka.http.scaladsl.server.Directives.authorize
import akka.http.scaladsl.server.Directives.complete
import akka.http.scaladsl.server.Directives.get
import akka.http.scaladsl.server.Directives.pathEnd
import akka.http.scaladsl.server.Directives.pathPrefix
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import com.convergencelabs.server.security.AuthorizationProfile

object DomainAdminTokenService {
  case class AdminTokenRestResponse(token: String)
}

class DomainAdminTokenService(
  executionContext: ExecutionContext,
  timeout: Timeout,
  private[this] val domainRestActor: ActorRef)
    extends DomainRestService(executionContext, timeout) {

  import DomainAdminTokenService._
  import akka.pattern.ask
  
  def route(authProfile: AuthorizationProfile, domain: DomainFqn): Route = {
    pathPrefix("adminToken") {
      pathEnd {
        get {
          authorize(canAccessDomain(domain, authProfile)) {
            complete(getAdminToken(domain, authProfile.username))
          }
        }
      }
    }
  }

  def getAdminToken(domain: DomainFqn, username: String): Future[RestResponse] = {
    val message = DomainRestMessage(domain, AdminTokenRequest(username))
    (domainRestActor ? message).mapTo[String] map {
      case token: String => okResponse(AdminTokenRestResponse(token))
    }
  }
}
