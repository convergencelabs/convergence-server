package com.convergencelabs.server.frontend.rest

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import com.convergencelabs.server.domain.DomainFqn
import com.convergencelabs.server.domain.RestDomainActor.AdminTokenRequest
import com.convergencelabs.server.domain.RestDomainManagerActor.DomainMessage
import com.convergencelabs.server.frontend.rest.DomainAdminTokenService.AdminTokenRestResponse

import akka.actor.ActorRef
import akka.http.scaladsl.marshalling.ToResponseMarshallable.apply
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directive.addByNameNullaryApply
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import akka.util.Timeout

object DomainAdminTokenService {
  case class AdminTokenRestResponse(token: String) extends AbstractSuccessResponse
}

class DomainAdminTokenService(
  private[this] val executionContext: ExecutionContext,
  private[this] val domainRestActor: ActorRef,
  private[this] val defaultTimeout: Timeout)
    extends JsonSupport {

  implicit val ec = executionContext
  implicit val t = defaultTimeout

  def route(username: String, domain: DomainFqn): Route = {
    pathPrefix("adminToken") {
      pathEnd {
        get {
          complete(getAdminToken(domain, username))
        }
      }
    }
  }

  def getAdminToken(domain: DomainFqn, username: String): Future[RestResponse] = {
    val message = DomainMessage(domain, AdminTokenRequest(username))
    (domainRestActor ? message).mapTo[String] map {
      case token: String => (StatusCodes.OK, AdminTokenRestResponse(token))
    }
  }
}
