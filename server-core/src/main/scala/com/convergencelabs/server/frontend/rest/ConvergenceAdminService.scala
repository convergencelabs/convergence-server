package com.convergencelabs.server.frontend.rest

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import akka.actor.ActorRef
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.model.StatusCodes
import akka.pattern.ask
import akka.util.Timeout
import com.convergencelabs.server.frontend.rest.ConvergenceAdminService.CreateUserRequest
import com.convergencelabs.server.datastore.ConvergenceUserManagerActor.CreateConvergenceUserRequest
import com.convergencelabs.server.datastore.CreateResult
import com.convergencelabs.server.datastore.CreateSuccess
import com.convergencelabs.server.datastore.DuplicateValue
import com.convergencelabs.server.datastore.InvalidValue
import com.convergencelabs.server.datastore.DeleteResult
import com.convergencelabs.server.datastore.DeleteSuccess
import com.convergencelabs.server.datastore.ConvergenceUserManagerActor.DeleteConvergenceUserRequest
import com.convergencelabs.server.datastore.NotFound
import com.convergencelabs.server.datastore.ConvergenceUserManagerActor.GetConvergenceUsers
import com.convergencelabs.server.User
import com.convergencelabs.server.frontend.rest.ConvergenceAdminService.GetUsersResponse
import com.convergencelabs.server.datastore.ConvergenceUserManagerActor.GetConvergenceUser
import com.convergencelabs.server.frontend.rest.ConvergenceAdminService.GetUserResponse

object ConvergenceAdminService {
  case class CreateUserRequest(username: String, firstName: String, lastName: String, email: String, password: String)
  case class GetUsersResponse(users: List[User]) extends AbstractSuccessResponse
  case class GetUserResponse(user: Option[User]) extends AbstractSuccessResponse
}

class ConvergenceAdminService(
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
    val CreateUserRequest(username, firstName, lastName, email, password) = createRequest
    (userManagerActor ? CreateConvergenceUserRequest(username, email, firstName, lastName, password)).mapTo[CreateResult[String]].map {
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


