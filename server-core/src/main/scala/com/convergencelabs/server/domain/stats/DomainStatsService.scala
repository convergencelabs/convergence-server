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
import com.convergencelabs.server.datastore.UserStoreActor.CreateUser
import com.convergencelabs.server.datastore.UserStoreActor.DeleteDomainUser
import com.convergencelabs.server.datastore.UserStoreActor.GetUserByUsername
import com.convergencelabs.server.datastore.UserStoreActor.GetUsers
import com.convergencelabs.server.datastore.UserStoreActor.SetPassword
import com.convergencelabs.server.datastore.UserStoreActor.UpdateUser
import com.convergencelabs.server.domain.DomainFqn
import com.convergencelabs.server.domain.DomainUser
import com.convergencelabs.server.domain.RestDomainManagerActor.DomainMessage
import com.convergencelabs.server.frontend.rest.DomainUserService.CreateUserRequest
import com.convergencelabs.server.frontend.rest.DomainUserService.CreateUserResponse
import com.convergencelabs.server.frontend.rest.DomainUserService.GetUserRestResponse
import com.convergencelabs.server.frontend.rest.DomainUserService.SetPasswordRequest
import com.convergencelabs.server.frontend.rest.DomainUserService.UpdateUserRequest

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
import com.convergencelabs.server.domain.stats.DomainStatsActor.DomainStats
import com.convergencelabs.server.domain.stats.DomainStatsActor.GetStats
import com.convergencelabs.server.frontend.rest.DomainStatsService.GetStatsResponse


object DomainStatsService {
  case class GetStatsResponse(stats: DomainStats) extends AbstractSuccessResponse
}

class DomainStatsService(
  private[this] val executionContext: ExecutionContext,
  private[this] val domainRestActor: ActorRef,
  private[this] val defaultTimeout: Timeout)
    extends JsonSupport {

  implicit val ec = executionContext
  implicit val t = defaultTimeout

  def route(username: String, domain: DomainFqn): Route = {
    pathPrefix("stats") {
      pathEnd {
        get {
          complete(getStats(domain))
        } 
      } 
    }
  }

  def getStats(domain: DomainFqn): Future[RestResponse] = {
    (domainRestActor ? DomainMessage(domain, GetStats)).mapTo[DomainStats] map 
    (stats => (StatusCodes.OK, GetStatsResponse(stats)))
  }
}
