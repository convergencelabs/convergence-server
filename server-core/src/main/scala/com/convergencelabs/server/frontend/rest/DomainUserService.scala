package com.convergencelabs.server.frontend.rest

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success
import scala.util.Try
import com.convergencelabs.server.domain.DomainFqn
import com.convergencelabs.server.domain.DomainUser
import com.convergencelabs.server.domain.RestDomainActor.DomainMessage
import akka.actor.ActorRef
import akka.http.scaladsl.marshalling.ToResponseMarshallable.apply
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directive.addByNameNullaryApply
import akka.http.scaladsl.server.Directives.complete
import akka.http.scaladsl.server.Directives.get
import akka.http.scaladsl.server.Directives.pathPrefix
import akka.http.scaladsl.server.Directives.pathEnd
import akka.http.scaladsl.server.Directives.segmentStringToPathMatcher
import akka.pattern.ask
import akka.util.Timeout
import com.convergencelabs.server.datastore.UserStoreActor.GetUsersResponse
import com.convergencelabs.server.datastore.UserStoreActor.GetUsers
import com.convergencelabs.server.datastore.UserStoreActor.GetUsersResponse
import akka.http.scaladsl.server.Route

object DomainUserService {
  case class GetUsersRestResponse(users: List[DomainUser]) extends AbstractSuccessResponse
}

class DomainUserService(
  private[this] val executionContext: ExecutionContext,
  private[this] val domainRestActor: ActorRef,
  private[this] val defaultTimeout: Timeout)
    extends JsonSupport {

  import DomainUserService._

  implicit val ec = executionContext
  implicit val t = defaultTimeout

  def route(userId: String, domain: DomainFqn): Route = {
    pathPrefix("users") {
      pathEnd {
        get {
          complete(getAllUsersRequest(domain))
        }
      }
    }
  }

  def getAllUsersRequest(domain: DomainFqn): Future[RestResponse] = {
    (domainRestActor ? DomainMessage(domain, GetUsers)).mapTo[GetUsersResponse] map {
      case GetUsersResponse(users) => (StatusCodes.OK, GetUsersRestResponse(users))
    }
  }
}
