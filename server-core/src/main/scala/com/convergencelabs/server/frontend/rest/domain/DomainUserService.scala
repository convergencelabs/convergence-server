package com.convergencelabs.server.frontend.rest

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import com.convergencelabs.server.datastore.domain.UserStoreActor.CreateUser
import com.convergencelabs.server.datastore.domain.UserStoreActor.DeleteDomainUser
import com.convergencelabs.server.datastore.domain.UserStoreActor.FindUser
import com.convergencelabs.server.datastore.domain.UserStoreActor.GetUserByUsername
import com.convergencelabs.server.datastore.domain.UserStoreActor.GetUsers
import com.convergencelabs.server.datastore.domain.UserStoreActor.SetPassword
import com.convergencelabs.server.datastore.domain.UserStoreActor.UpdateUser
import com.convergencelabs.server.domain.DomainFqn
import com.convergencelabs.server.domain.DomainUser
import com.convergencelabs.server.domain.rest.RestDomainActor.DomainRestMessage

import akka.actor.ActorRef
import akka.http.scaladsl.marshalling.ToResponseMarshallable.apply
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import com.convergencelabs.server.domain.DomainUserType
import com.convergencelabs.server.domain.DomainUserId
import io.convergence.proto.identity.DomainUserIdData
import com.convergencelabs.server.frontend.realtime.ImplicitMessageConversions

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
  case class UserLookupRequest(filter: String, exclude: Option[List[String]], offset: Option[Int], limit: Option[Int])

  case class DomainUserData(
    username: String,
    firstName: Option[String],
    lastName: Option[String],
    displayName: Option[String],
    email: Option[String],
    disabled: Boolean,
    deleted: Boolean,
    deletedUsername: Option[String])
}

class DomainUserService(
  private[this] val executionContext: ExecutionContext,
  private[this] val timeout: Timeout,
  private[this] val authActor: ActorRef,
  private[this] val domainRestActor: ActorRef)
    extends DomainRestService(executionContext, timeout, authActor) {

  import DomainUserService._
  import akka.http.scaladsl.server.Directive._
  import akka.http.scaladsl.server.Directives._
  import akka.pattern.ask

  def route(convergenceUsername: String, domain: DomainFqn): Route = {
    pathPrefix("users") {
      pathEnd {
        get {
          parameters("filter".?, "limit".as[Int].?, "offset".as[Int].?) { (filter, limit, offset) =>
            authorizeAsync(canAccessDomain(domain, convergenceUsername)) {
              complete(getAllUsersRequest(domain, filter, limit, offset))
            }
          }
        } ~ post {
          entity(as[CreateUserRequest]) { request =>
            authorizeAsync(canAccessDomain(domain, convergenceUsername)) {
              complete(createUserRequest(request, domain))
            }
          }
        }
      } ~ pathPrefix(Segment) { domainUsername =>
        pathEnd {
          get {
            authorizeAsync(canAccessDomain(domain, convergenceUsername)) {
              complete(getUserByUsername(domainUsername, domain))
            }
          } ~ delete {
            authorizeAsync(canAccessDomain(domain, convergenceUsername)) {
              complete(deleteUser(domainUsername, domain))
            }
          } ~ put {
            entity(as[UpdateUserRequest]) { request =>
              authorizeAsync(canAccessDomain(domain, convergenceUsername)) {
                complete(updateUserRequest(domainUsername, request, domain))
              }
            }
          }
        } ~ pathPrefix("password") {
          pathEnd {
            put {
              entity(as[SetPasswordRequest]) { request =>
                authorizeAsync(canAccessDomain(domain, convergenceUsername)) {
                  complete(setPasswordRequest(domainUsername, request, domain))
                }
              }
            }
          }
        }
      }
    } ~ pathPrefix("user-lookup") {
      pathEnd {
        post {
          entity(as[UserLookupRequest]) { request =>
            authorizeAsync(canAccessDomain(domain, convergenceUsername)) {
              complete(findUser(domain, request))
            }
          }
        } ~ post {
          entity(as[CreateUserRequest]) { request =>
            authorizeAsync(canAccessDomain(domain, convergenceUsername)) {
              complete(createUserRequest(request, domain))
            }
          }
        }
      }
    }
  }

  def getAllUsersRequest(domain: DomainFqn, filter: Option[String], limit: Option[Int], offset: Option[Int]): Future[RestResponse] = {
    (domainRestActor ? DomainRestMessage(domain, GetUsers(filter, limit, offset))).mapTo[List[DomainUser]] map
      (users => (StatusCodes.OK, GetUsersRestResponse(users.map(toUserData(_)))))
  }

  def findUser(domain: DomainFqn, request: UserLookupRequest): Future[RestResponse] = {
    val UserLookupRequest(filter, excludes, offset, limit) = request
    val findUser = FindUser(filter, excludes.map(_.map(DomainUserId(DomainUserType.Normal, _))), offset, limit)
    (domainRestActor ? DomainRestMessage(domain, findUser)).mapTo[List[DomainUser]] map
      (users => (StatusCodes.OK, GetUsersRestResponse(users.map(toUserData(_)))))
  }

  def createUserRequest(createRequest: CreateUserRequest, domain: DomainFqn): Future[RestResponse] = {
    val CreateUserRequest(username, firstName, lastName, displayName, email, password) = createRequest
    val message = DomainRestMessage(domain, CreateUser(username, firstName, lastName, displayName, email, password))
    (domainRestActor ? message) map { _ => (StatusCodes.Created, CreateUserResponse()) }
  }

  def updateUserRequest(username: String, updateRequest: UpdateUserRequest, domain: DomainFqn): Future[RestResponse] = {
    val UpdateUserRequest(firstName, lastName, displayName, email) = updateRequest
    val message = DomainRestMessage(domain, UpdateUser(username, firstName, lastName, displayName, email))
    (domainRestActor ? message) map { _ => OkResponse }
  }

  def setPasswordRequest(uid: String, setPasswordRequest: SetPasswordRequest, domain: DomainFqn): Future[RestResponse] = {
    val message = DomainRestMessage(domain, SetPassword(uid, setPasswordRequest.password))
    (domainRestActor ? message) map { _ => OkResponse }
  }

  def getUserByUsername(username: String, domain: DomainFqn): Future[RestResponse] = {
    val message = DomainRestMessage(domain, GetUserByUsername(DomainUserId(DomainUserType.Normal, username)))
    (domainRestActor ? message).mapTo[Option[DomainUser]] map {
      case Some(user) =>
        (StatusCodes.OK, GetUserRestResponse(toUserData(user)))
      case None =>
        notFoundResponse()
    }
  }

  def deleteUser(uid: String, domain: DomainFqn): Future[RestResponse] = {
    (domainRestActor ? DomainRestMessage(domain, DeleteDomainUser(uid))) map { _ => OkResponse }
  }

  private[this] def toUserData(user: DomainUser): DomainUserData = {
    val DomainUser(userType, username, firstName, lastName, displayName, email, disabled, deleted, deletedUsername) = user
    val userId = DomainUserId(userType, username)
    DomainUserData(userId.username, firstName, lastName, displayName, email, disabled, deleted, deletedUsername)
  }
}