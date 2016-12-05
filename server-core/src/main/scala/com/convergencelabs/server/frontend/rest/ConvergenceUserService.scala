package com.convergencelabs.server.frontend.rest

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import com.convergencelabs.server.datastore.User
import com.convergencelabs.server.datastore.ConvergenceUserManagerActor.CreateConvergenceUserRequest
import com.convergencelabs.server.datastore.ConvergenceUserManagerActor.DeleteConvergenceUserRequest
import com.convergencelabs.server.datastore.ConvergenceUserManagerActor.GetConvergenceUser
import com.convergencelabs.server.datastore.ConvergenceUserManagerActor.GetConvergenceUsers
import com.convergencelabs.server.datastore.CreateResult
import com.convergencelabs.server.datastore.CreateSuccess
import com.convergencelabs.server.datastore.DeleteResult
import com.convergencelabs.server.datastore.DeleteSuccess
import com.convergencelabs.server.datastore.DuplicateValue
import com.convergencelabs.server.datastore.InvalidValue
import com.convergencelabs.server.datastore.NotFound
import com.convergencelabs.server.frontend.rest.ConvergenceUserService.CreateUserRequest
import com.convergencelabs.server.frontend.rest.ConvergenceUserService.GetUserResponse
import com.convergencelabs.server.frontend.rest.ConvergenceUserService.GetUsersResponse

import akka.actor.ActorRef
import akka.http.scaladsl.marshalling.ToResponseMarshallable.apply
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directive.addByNameNullaryApply
import akka.http.scaladsl.server.Directive.addDirectiveApply
import akka.http.scaladsl.server.Directives.Segment
import akka.http.scaladsl.server.Directives._enhanceRouteWithConcatenation
import akka.http.scaladsl.server.Directives._segmentStringToPathMatcher
import akka.http.scaladsl.server.Directives.complete
import akka.http.scaladsl.server.Directives.delete
import akka.http.scaladsl.server.Directives.get
import akka.http.scaladsl.server.Directives.handleWith
import akka.http.scaladsl.server.Directives.pathEnd
import akka.http.scaladsl.server.Directives.pathPrefix
import akka.http.scaladsl.server.Directives.post
import akka.pattern.ask
import akka.util.Timeout

object ConvergenceUserService {
  case class CreateUserRequest(username: String, firstName: String, lastName: String, displayName: String, email: String, password: String)
  case class GetUsersResponse(users: List[User]) extends AbstractSuccessResponse
  case class GetUserResponse(user: Option[User]) extends AbstractSuccessResponse
}

class ConvergenceUserService(
    private[this] val executionContext: ExecutionContext,
    private[this] val userManagerActor: ActorRef,
    private[this] val defaultTimeout: Timeout) extends JsonSupport {

  implicit val ec = executionContext
  implicit val t = defaultTimeout

  val route = { adminUser: String =>
    pathPrefix("users") {
      pathEnd {
        get {
          complete(getUsersRequest())
        } ~ post {
          handleWith(createConvergenceUserRequest)
        }
      } ~ pathPrefix(Segment) { username =>
        pathEnd {
          delete {
            complete(deleteConvergenceUserRequest(username))
          } ~ get {
            complete(getUser(username))
          }
        }
      }
    }
  }

  def createConvergenceUserRequest(createRequest: CreateUserRequest): Future[RestResponse] = {
    val CreateUserRequest(username, firstName, lastName, email, displayName, password) = createRequest
    (userManagerActor ? CreateConvergenceUserRequest(username, email, firstName, lastName, displayName, password)).mapTo[CreateResult[String]].map {
      case result: CreateSuccess[String] => CreateRestResponse
      case DuplicateValue => DuplicateError
      case InvalidValue => InvalidValueError
    }
  }

  def deleteConvergenceUserRequest(username: String): Future[RestResponse] = {
    (userManagerActor ? DeleteConvergenceUserRequest(username)).mapTo[DeleteResult].map {
      case DeleteSuccess => OkResponse
      case NotFound => NotFoundError
    }
  }

  def getUsersRequest(): Future[RestResponse] = {
    (userManagerActor ? GetConvergenceUsers).mapTo[List[User]] map
      (users => (StatusCodes.OK, GetUsersResponse(users)))
  }

  def getUser(username: String): Future[RestResponse] = {
    (userManagerActor ? GetConvergenceUser(username)).mapTo[Option[User]] map
      (user => (StatusCodes.OK, GetUserResponse(user)))
  }
}
