package com.convergencelabs.server.frontend.rest

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success
import scala.util.Try

import com.convergencelabs.server.datastore.UserStoreActor.GetAllUsersRequest
import com.convergencelabs.server.datastore.UserStoreActor.GetAllUsersResponse
import com.convergencelabs.server.domain.DomainFqn
import com.convergencelabs.server.domain.DomainUser
import com.convergencelabs.server.domain.RestDomainActor.DomainMessage

import DomainUserService.GetUsersResponse
import akka.actor.ActorRef
import akka.http.scaladsl.marshalling.ToResponseMarshallable.apply
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directive.addByNameNullaryApply
import akka.http.scaladsl.server.Directives.complete
import akka.http.scaladsl.server.Directives.get
import akka.http.scaladsl.server.Directives.pathPrefix
import akka.http.scaladsl.server.Directives.segmentStringToPathMatcher
import akka.pattern.ask
import akka.util.Timeout

object DomainUserService {
  case class GetUsersResponse(ok: Boolean, users: List[DomainUser]) extends ResponseMessage
}

class DomainUserService(
  private[this] val executionContext: ExecutionContext,
  private[this] val domainRestActor: ActorRef,
  private[this] val defaultTimeout: Timeout)
    extends JsonSupport {

  import DomainUserService._

  implicit val ec = executionContext
  implicit val t = defaultTimeout

  def route(userId: String, namespace: String, domainId: String) = {
    pathPrefix("users") {
      get {
        complete(getAllUsersRequest(namespace, domainId))
      }
    }
  }

  def getAllUsersRequest(namespace: String, domainId: String): Future[RestResponse] = {
    (domainRestActor ? DomainMessage(DomainFqn(namespace, domainId), GetAllUsersRequest())).mapTo[GetAllUsersResponse] map {
      case GetAllUsersResponse(users) => (StatusCodes.OK, GetUsersResponse(true, users))
    }
  }
}
