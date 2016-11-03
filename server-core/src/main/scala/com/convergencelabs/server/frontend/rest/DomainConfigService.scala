package com.convergencelabs.server.frontend.rest

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import com.convergencelabs.server.datastore.CreateResult
import com.convergencelabs.server.datastore.CreateSuccess
import com.convergencelabs.server.datastore.DeleteResult
import com.convergencelabs.server.datastore.DeleteSuccess
import com.convergencelabs.server.datastore.DuplicateValue
import com.convergencelabs.server.datastore.InvalidValue
import com.convergencelabs.server.datastore.NotFound
import com.convergencelabs.server.datastore.UpdateResult
import com.convergencelabs.server.datastore.UpdateSuccess
import com.convergencelabs.server.domain.DomainFqn
import com.convergencelabs.server.domain.DomainUser
import com.convergencelabs.server.domain.RestDomainManagerActor.DomainMessage

import DomainUserService.GetUsersRestResponse
import akka.actor.ActorRef
import akka.http.scaladsl.marshalling.ToResponseMarshallable.apply
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directive.addByNameNullaryApply
import akka.http.scaladsl.server.Directive.addDirectiveApply
import akka.http.scaladsl.server.Directives.Segment
import akka.http.scaladsl.server.Directives._enhanceRouteWithConcatenation
import akka.http.scaladsl.server.Directives._segmentStringToPathMatcher
import akka.http.scaladsl.server.Directives.as
import akka.http.scaladsl.server.Directives.complete
import akka.http.scaladsl.server.Directives.delete
import akka.http.scaladsl.server.Directives.entity
import akka.http.scaladsl.server.Directives.get
import akka.http.scaladsl.server.Directives.pathEnd
import akka.http.scaladsl.server.Directives.pathPrefix
import akka.http.scaladsl.server.Directives.post
import akka.http.scaladsl.server.Directives.put
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import akka.util.Timeout
import com.convergencelabs.server.frontend.rest.DomainConfigService.AnonymousAuthPut
import com.convergencelabs.server.datastore.ConfigStoreActor.GetAnonymousAuth
import com.convergencelabs.server.frontend.rest.DomainConfigService.AnonymousAuthResponse
import com.convergencelabs.server.datastore.ConfigStoreActor.SetAnonymousAuth

object DomainConfigService {
  case class AnonymousAuthPut(enabled: Boolean)
  case class AnonymousAuthResponse(enabled: Boolean) extends AbstractSuccessResponse
}

class DomainConfigService(
  private[this] val executionContext: ExecutionContext,
  private[this] val domainRestActor: ActorRef,
  private[this] val defaultTimeout: Timeout)
    extends JsonSupport {

  implicit val ec = executionContext
  implicit val t = defaultTimeout

  def route(username: String, domain: DomainFqn): Route = {
    pathPrefix("config") {
      pathPrefix("anonymousAuth") {
        pathEnd {
          get {
            complete(getAnonymousAuthEnabled(domain))
          } ~ put {
            entity(as[AnonymousAuthPut]) { request =>
              complete(setAnonymousAuthEnabled(domain, request))
            }
          }
        } 
      }
    }
  }

  def getAnonymousAuthEnabled(domain: DomainFqn): Future[RestResponse] = {
    (domainRestActor ? DomainMessage(domain, GetAnonymousAuth)).mapTo[Boolean] map 
    (enabled => (StatusCodes.OK, AnonymousAuthResponse(enabled)))
  }
  
  def setAnonymousAuthEnabled(domain: DomainFqn, request: AnonymousAuthPut): Future[RestResponse] = {
    (domainRestActor ? DomainMessage(domain, SetAnonymousAuth(request.enabled))).mapTo[Unit] map 
    (_ => OkResponse)
  }
}
