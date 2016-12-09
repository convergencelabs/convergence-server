package com.convergencelabs.server.frontend.rest

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

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

case class DomainUserData(
  username: String,
  firstName: Option[String],
  lastName: Option[String],
  displayName: Option[String],
  email: Option[String])

object DomainUserService {
  case class CreateUserRequest(username: String, firstName: Option[String], lastName: Option[String], displayName: Option[String], email: Option[String], password: Option[String])
  case class CreateUserResponse() extends AbstractSuccessResponse
  case class GetUsersRestResponse(users: List[DomainUserData]) extends AbstractSuccessResponse
  case class GetUserRestResponse(user: DomainUserData) extends AbstractSuccessResponse
  case class UpdateUserRequest(
    firstName: Option[String],
    lastName: Option[String],
    displayName: Option[String],
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

  def route(username: String, domain: DomainFqn): Route = {
    pathPrefix("users") {
      pathEnd {
        get {
          complete(getAllUsersRequest(domain))
        } ~ post {
          entity(as[CreateUserRequest]) { request =>
            complete(createUserRequest(request, domain))
          }
        }
      } ~ pathPrefix(Segment) { username =>
        pathEnd {
          get {
            complete(getUserByUsername(username, domain))
          } ~ delete {
            complete(deleteUser(username, domain))
          } ~ put {
            entity(as[UpdateUserRequest]) { request =>
              complete(updateUserRequest(username, request, domain))
            }
          }
        } ~ pathPrefix("password") {
          pathEnd {
            put {
              entity(as[SetPasswordRequest]) { request =>
                complete(setPasswordRequest(username, request, domain))
              }
            }
          }
        }
      }
    }
  }

  def getAllUsersRequest(domain: DomainFqn): Future[RestResponse] = {
    (domainRestActor ? DomainMessage(domain, GetUsers)).mapTo[List[DomainUser]] map
      (users => (StatusCodes.OK, GetUsersRestResponse(users.map(toUserData(_)))))
  }

  def createUserRequest(createRequest: CreateUserRequest, domain: DomainFqn): Future[RestResponse] = {
    val CreateUserRequest(username, firstName, lastName, displayName, email, password) = createRequest
    val message = DomainMessage(domain, CreateUser(username, firstName, lastName, displayName, email, password))
    (domainRestActor ? message) map { _ => (StatusCodes.Created, CreateUserResponse()) }
  }

  def updateUserRequest(username: String, updateRequest: UpdateUserRequest, domain: DomainFqn): Future[RestResponse] = {
    val UpdateUserRequest(firstName, lastName, displayName, email) = updateRequest
    val message = DomainMessage(domain, UpdateUser(username, firstName, lastName, displayName, email))
    (domainRestActor ? message) map { _ => OkResponse }
  }

  def setPasswordRequest(uid: String, setPasswordRequest: SetPasswordRequest, domain: DomainFqn): Future[RestResponse] = {
    val message = DomainMessage(domain, SetPassword(uid, setPasswordRequest.password))
    (domainRestActor ? message) map { _ => OkResponse }
  }

  def getUserByUsername(username: String, domain: DomainFqn): Future[RestResponse] = {
    (domainRestActor ? DomainMessage(domain, GetUserByUsername(username))).mapTo[Option[DomainUser]] map {
      case Some(user) =>
        (StatusCodes.OK, GetUserRestResponse(toUserData(user)))
      case None =>
        NotFoundError
    }
  }

  def deleteUser(uid: String, domain: DomainFqn): Future[RestResponse] = {
    (domainRestActor ? DomainMessage(domain, DeleteDomainUser(uid))) map { _ => OkResponse }
  }

  private[this] def toUserData(user: DomainUser): DomainUserData = {
    val DomainUser(userType, username, firstName, lastName, displayName, email) = user
    DomainUserData(username, firstName, lastName, displayName, email)
  }
}
