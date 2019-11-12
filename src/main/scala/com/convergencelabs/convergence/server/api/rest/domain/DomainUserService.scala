/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is part of the Convergence Server, which is released under
 * the terms of the GNU General Public License version 3 (GPLv3). A copy
 * of the GPLv3 should have been provided along with this file, typically
 * located in the "LICENSE" file, which is part of this source code package.
 * Alternatively, see <https://www.gnu.org/licenses/gpl-3.0.html> for the
 * full text of the GPLv3 license, if it was not provided.
 */

package com.convergencelabs.convergence.server.api.rest.domain

import akka.actor.ActorRef
import akka.http.scaladsl.marshalling.ToResponseMarshallable.apply
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import com.convergencelabs.convergence.server.api.rest._
import com.convergencelabs.convergence.server.datastore.domain.UserStoreActor._
import com.convergencelabs.convergence.server.domain.{DomainId, DomainUser, DomainUserId, DomainUserType}
import com.convergencelabs.convergence.server.domain.rest.RestDomainActor.DomainRestMessage
import com.convergencelabs.convergence.server.security.AuthorizationProfile

import scala.concurrent.{ExecutionContext, Future}

object DomainUserService {
  case class CreateUserRequest(username: String, firstName: Option[String], lastName: Option[String], displayName: Option[String], email: Option[String], password: Option[String])
  case class UpdateUserRequest(
    firstName: Option[String],
    lastName: Option[String],
    displayName: Option[String],
    email: Option[String],
    disabled: Option[Boolean])
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
  private[this] val domainRestActor: ActorRef)
  extends DomainRestService(executionContext, timeout) {

  import DomainUserService._
  import akka.http.scaladsl.server.Directive._
  import akka.http.scaladsl.server.Directives._
  import akka.pattern.ask

  def route(authProfile: AuthorizationProfile, domain: DomainId): Route = {
    pathPrefix("users") {
      pathEnd {
        get {
          parameters("filter".?, "offset".as[Int].?, "limit".as[Int].?) { (filter, offset, limit) =>
            complete(getAllUsersRequest(domain, filter, offset, limit))
          }
        } ~ post {
          entity(as[CreateUserRequest]) { request =>
            complete(createUserRequest(request, domain))
          }
        }
      } ~ pathPrefix(Segment) { domainUsername =>
        pathEnd {
          get {
            complete(getUserByUsername(domainUsername, domain))
          } ~ delete {
            complete(deleteUser(domainUsername, domain))
          } ~ put {
            entity(as[UpdateUserRequest]) { request =>
              complete(updateUserRequest(domainUsername, request, domain))
            }
          }
        } ~ pathPrefix("password") {
          pathEnd {
            put {
              entity(as[SetPasswordRequest]) { request =>
                complete(setPasswordRequest(domainUsername, request, domain))
              }
            }
          }
        }
      }
    } ~ pathPrefix("user-lookup") {
      pathEnd {
        post {
          entity(as[UserLookupRequest]) { request =>
            complete(findUser(domain, request))
          }
        } ~ post {
          entity(as[CreateUserRequest]) { request =>
            complete(createUserRequest(request, domain))
          }
        }
      }
    }
  }

  def getAllUsersRequest(domain: DomainId, filter: Option[String], offset: Option[Int], limit: Option[Int]): Future[RestResponse] = {
    (domainRestActor ? DomainRestMessage(domain, GetUsers(filter, offset, limit))).mapTo[List[DomainUser]] map
      (users => okResponse(users.map(toUserData)))
  }

  def findUser(domain: DomainId, request: UserLookupRequest): Future[RestResponse] = {
    val UserLookupRequest(filter, excludes, offset, limit) = request
    val findUser = FindUser(filter, excludes.map(_.map(DomainUserId(DomainUserType.Normal, _))), offset, limit)
    (domainRestActor ? DomainRestMessage(domain, findUser)).mapTo[List[DomainUser]] map
      (users => okResponse(users.map(toUserData)))
  }

  def createUserRequest(createRequest: CreateUserRequest, domain: DomainId): Future[RestResponse] = {
    val CreateUserRequest(username, firstName, lastName, displayName, email, password) = createRequest
    val message = DomainRestMessage(domain, CreateUser(username, firstName, lastName, displayName, email, password))
    (domainRestActor ? message) map { _ => CreatedResponse }
  }

  def updateUserRequest(username: String, updateRequest: UpdateUserRequest, domain: DomainId): Future[RestResponse] = {
    val UpdateUserRequest(firstName, lastName, displayName, email, disabled) = updateRequest
    val message = DomainRestMessage(domain, UpdateUser(username, firstName, lastName, displayName, email, disabled))
    (domainRestActor ? message) map { _ => OkResponse }
  }

  def setPasswordRequest(uid: String, setPasswordRequest: SetPasswordRequest, domain: DomainId): Future[RestResponse] = {
    val message = DomainRestMessage(domain, SetPassword(uid, setPasswordRequest.password))
    (domainRestActor ? message) map { _ => OkResponse }
  }

  def getUserByUsername(username: String, domain: DomainId): Future[RestResponse] = {
    val message = DomainRestMessage(domain, GetUserByUsername(DomainUserId(DomainUserType.Normal, username)))
    (domainRestActor ? message).mapTo[Option[DomainUser]] map {
      case Some(user) =>
        okResponse(toUserData(user))
      case None =>
        notFoundResponse()
    }
  }

  def deleteUser(uid: String, domain: DomainId): Future[RestResponse] = {
    (domainRestActor ? DomainRestMessage(domain, DeleteDomainUser(uid))) map { _ => OkResponse }
  }

  private[this] def toUserData(user: DomainUser): DomainUserData = {
    val DomainUser(userType, username, firstName, lastName, displayName, email, disabled, deleted, deletedUsername) = user
    val userId = DomainUserId(userType, username)
    DomainUserData(userId.username, firstName, lastName, displayName, email, disabled, deleted, deletedUsername)
  }
}