package com.convergencelabs.server.frontend.rest

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import com.convergencelabs.server.datastore.UserStoreActor.GetUsers
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
import akka.http.scaladsl.server.Directives.as
import akka.http.scaladsl.server.Directives.complete
import akka.http.scaladsl.server.Directives.delete
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Directives.entity
import akka.http.scaladsl.server.Directives.get
import akka.http.scaladsl.server.Directives.pathEnd
import akka.http.scaladsl.server.Directives.pathPrefix
import akka.http.scaladsl.server.Directives.post
import akka.http.scaladsl.server.Directives.put
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import akka.util.Timeout
import com.convergencelabs.server.frontend.rest.DomainUserService.CreateUserRequest
import com.convergencelabs.server.datastore.UserStoreActor.CreateUser
import com.convergencelabs.server.datastore.CreateResult
import com.convergencelabs.server.datastore.CreateSuccess
import com.convergencelabs.server.datastore.DuplicateValue
import com.convergencelabs.server.frontend.rest.DomainUserService.CreateUserResponse
import com.convergencelabs.server.datastore.UserStoreActor.GetUserByUid
import com.convergencelabs.server.frontend.rest.DomainUserService.GetUserRestResponse
import com.convergencelabs.server.datastore.DeleteResult
import com.convergencelabs.server.datastore.DeleteSuccess
import com.convergencelabs.server.datastore.NotFound
import com.convergencelabs.server.datastore.UserStoreActor.DeleteDomainUser
import com.convergencelabs.server.datastore.InvalidValue
import com.convergencelabs.server.frontend.rest.DomainUserService.UpdateUserRequest
import com.convergencelabs.server.datastore.UserStoreActor.UpdateUser
import com.convergencelabs.server.datastore.UpdateResult
import com.convergencelabs.server.datastore.UpdateSuccess
import com.convergencelabs.server.frontend.rest.DomainUserService.SetPasswordRequest
import com.convergencelabs.server.datastore.UserStoreActor.SetPassword

object DomainUserService {
  case class CreateUserRequest(username: String, firstName: Option[String], lastName: Option[String], email: Option[String], password: Option[String])
  case class CreateUserResponse(uid: String) extends AbstractSuccessResponse
  case class GetUsersRestResponse(users: List[DomainUser]) extends AbstractSuccessResponse
  case class GetUserRestResponse(user: DomainUser) extends AbstractSuccessResponse
  case class UpdateUserRequest(
    username: String,
    firstName: Option[String],
    lastName: Option[String],
    email: Option[String])
  case class SetPasswordRequest(password: String)

}

class DomainUserService(
  private[this] val executionContext: ExecutionContext,
  private[this] val domainRestActor: ActorRef,
  private[this] val defaultTimeout: Timeout)
    extends JsonSupport {

  implicit val ec = executionContext
  implicit val t = defaultTimeout

  def route(userId: String, domain: DomainFqn): Route = {
    pathPrefix("users") {
      pathEnd {
        get {
          complete(getAllUsersRequest(domain))
        } ~ post {
          entity(as[CreateUserRequest]) { request =>
            complete(createUserRequest(request, domain))
          }
        }
      } ~ pathPrefix(Segment) { uid =>
        pathEnd {
          get {
            complete(getUserByUid(uid, domain))
          } ~ delete {
            complete(deleteUser(uid, domain))
          } ~ put {
            entity(as[UpdateUserRequest]) { request =>
              complete(updateUserRequest(uid, request, domain))
            }
          }
        } ~ pathPrefix("password") {
          pathEnd {
            put {
              entity(as[SetPasswordRequest]) { request =>
                complete(setPasswordRequest(uid, request, domain))
              }
            }
          }
        }
      }
    }
  }

  def getAllUsersRequest(domain: DomainFqn): Future[RestResponse] = {
    (domainRestActor ? DomainMessage(domain, GetUsers)).mapTo[List[DomainUser]] map (users => (StatusCodes.OK, GetUsersRestResponse(users)))
  }

  def createUserRequest(createRequest: CreateUserRequest, domain: DomainFqn): Future[RestResponse] = {
    val CreateUserRequest(username, firstName, lastName, email, password) = createRequest
    (domainRestActor ? DomainMessage(domain, CreateUser(username, firstName, lastName, email, password))).mapTo[CreateResult[String]].map {
      case result: CreateSuccess[String] => (StatusCodes.Created, CreateUserResponse(result.result))
      case DuplicateValue                => DuplicateError
      case InvalidValue                  => InvalidValueError
    }
  }

  def updateUserRequest(uid: String, updateRequest: UpdateUserRequest, domain: DomainFqn): Future[RestResponse] = {
    val UpdateUserRequest(username, firstName, lastName, email) = updateRequest
    (domainRestActor ? DomainMessage(domain, UpdateUser(uid, username, firstName, lastName, email))).mapTo[UpdateResult].map {
      case UpdateSuccess => OkResponse
      case NotFound      => NotFoundError
      case InvalidValue  => InvalidValueError
    }
  }

  def setPasswordRequest(uid: String, setPasswordRequest: SetPasswordRequest, domain: DomainFqn): Future[RestResponse] = {
    (domainRestActor ? DomainMessage(domain, SetPassword(uid, setPasswordRequest.password))).mapTo[UpdateResult].map {
      case UpdateSuccess => OkResponse
      case NotFound      => NotFoundError
      case InvalidValue  => InvalidValueError
    }
  }

  def getUserByUid(uid: String, domain: DomainFqn): Future[RestResponse] = {
    (domainRestActor ? DomainMessage(domain, GetUserByUid(uid))).mapTo[Option[DomainUser]] map {
      case Some(user) => (StatusCodes.OK, GetUserRestResponse(user))
      case None       => NotFoundError
    }
  }

  def deleteUser(uid: String, domain: DomainFqn): Future[RestResponse] = {
    (domainRestActor ? DomainMessage(domain, DeleteDomainUser(uid))).mapTo[DeleteResult] map {
      case DeleteSuccess => OkResponse
      case NotFound      => NotFoundError
    }
  }
}
