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

import java.time.Instant

import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.{ActorRef, Scheduler}
import akka.http.scaladsl.marshalling.ToResponseMarshallable.apply
import akka.http.scaladsl.server.Directive._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import com.convergencelabs.convergence.server.api.rest._
import com.convergencelabs.convergence.server.datastore.domain.UserStoreActor._
import com.convergencelabs.convergence.server.domain.rest.DomainRestActor
import com.convergencelabs.convergence.server.domain.rest.DomainRestActor.DomainRestMessage
import com.convergencelabs.convergence.server.domain.{DomainId, DomainUser, DomainUserId, DomainUserType}
import com.convergencelabs.convergence.server.security.AuthorizationProfile

import scala.concurrent.{ExecutionContext, Future}


class DomainUserService(domainRestActor: ActorRef[DomainRestActor.Message],
                        scheduler: Scheduler,
                        executionContext: ExecutionContext,
                        timeout: Timeout)
  extends AbstractDomainRestService(scheduler, executionContext, timeout) {

  import DomainUserService._

  def route(authProfile: AuthorizationProfile, domain: DomainId): Route = {
    pathPrefix("users") {
      pathEnd {
        get {
          parameters("filter".?, "offset".as[Int].?, "limit".as[Int].?) { (filter, offset, limit) =>
            complete(getAllUsersRequest(domain, filter, offset, limit))
          }
        } ~ post {
          entity(as[CreateUserRequestData]) { request =>
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
            entity(as[UpdateUserRequestData]) { request =>
              complete(updateUserRequest(domainUsername, request, domain))
            }
          }
        } ~ pathPrefix("password") {
          pathEnd {
            put {
              entity(as[SetPasswordRequestData]) { request =>
                complete(setPasswordRequest(domainUsername, request, domain))
              }
            }
          }
        }
      }
    }
  }

  private[this] def getAllUsersRequest(domain: DomainId, filter: Option[String], offset: Option[Int], limit: Option[Int]): Future[RestResponse] = {
    domainRestActor
      .ask[GetUsersResponse](
        r => DomainRestMessage(domain, GetUsersRequest(filter, offset, limit, r)))
      .map(_.users.fold(
        {
          case UnknownError() =>
            InternalServerError
        },
        // FIXME paged data
        users => okResponse(users.data.map(toUserData))
      ))
  }

  private[this] def createUserRequest(createRequest: CreateUserRequestData, domain: DomainId): Future[RestResponse] = {
    val CreateUserRequestData(username, firstName, lastName, displayName, email, password) = createRequest
    domainRestActor
      .ask[CreateUserResponse](
        r => DomainRestMessage(domain, CreateUserRequest(username, firstName, lastName, displayName, email, password, r)))
      .map(_.username.fold(
        {
          case UserAlreadyExistsError(field) =>
            duplicateResponse(field)
          case UnknownError() =>
            InternalServerError
        },
        _ => CreatedResponse
      ))
  }

  private[this] def updateUserRequest(username: String, updateRequest: UpdateUserRequestData, domain: DomainId): Future[RestResponse] = {
    val UpdateUserRequestData(firstName, lastName, displayName, email, disabled) = updateRequest
    domainRestActor
      .ask[UpdateUserResponse](
        r => DomainRestMessage(domain, UpdateUserRequest(username, firstName, lastName, displayName, email, disabled, r)))
      .map(_.response.fold(
        {
          case UserNotFoundError() =>
            userNotFound()
          case UnknownError() =>
            InternalServerError
        },
        _ => OkResponse
      ))
  }

  private[this] def setPasswordRequest(uid: String, setPasswordRequest: SetPasswordRequestData, domain: DomainId): Future[RestResponse] = {
    domainRestActor
      .ask[SetPasswordResponse](
        r => DomainRestMessage(domain, SetPasswordRequest(uid, setPasswordRequest.password, r)))
      .map(_.response.fold(
        {
          case UserNotFoundError() =>
            userNotFound()
          case UnknownError() =>
            InternalServerError
        },
        _ => OkResponse
      ))
  }

  private[this] def getUserByUsername(username: String, domain: DomainId): Future[RestResponse] = {
    val userId = DomainUserId(DomainUserType.Normal, username)
    domainRestActor
      .ask[GetUserResponse](
        r => DomainRestMessage(domain, GetUserRequest(userId, r)))
      .map(_.user.fold(
        {
          case UserNotFoundError() =>
            userNotFound()
          case UnknownError() =>
            InternalServerError
        },
        _ => okResponse(toUserData(_))
      ))
  }

  private[this] def deleteUser(uid: String, domain: DomainId): Future[RestResponse] = {
    domainRestActor
      .ask[DeleteUserResponse](
        r => DomainRestMessage(domain, DeleteUserRequest(uid, r)))
      .map(_.response.fold(
        {
          case UserNotFoundError() =>
            userNotFound()
          case UnknownError() =>
            InternalServerError
        },
        _ => DeletedResponse
      ))
  }

  private[this] def toUserData(user: DomainUser): DomainUserData = {
    val DomainUser(userType, username, firstName, lastName, displayName, email, lastLogin, disabled, deleted, deletedUsername) = user
    val userId = DomainUserId(userType, username)
    DomainUserData(userId.username, firstName, lastName, displayName, email, lastLogin, disabled, deleted, deletedUsername)
  }

  private[this] def userNotFound(): RestResponse = notFoundResponse("User not found")
}

object DomainUserService {

  case class CreateUserRequestData(username: String,
                                   firstName: Option[String],
                                   lastName: Option[String],
                                   displayName: Option[String],
                                   email: Option[String],
                                   password: Option[String])

  case class UpdateUserRequestData(firstName: Option[String],
                                   lastName: Option[String],
                                   displayName: Option[String],
                                   email: Option[String],
                                   disabled: Option[Boolean])

  case class SetPasswordRequestData(password: String)

  case class DomainUserData(username: String,
                            firstName: Option[String],
                            lastName: Option[String],
                            displayName: Option[String],
                            email: Option[String],
                            lastLogin: Option[Instant],
                            disabled: Boolean,
                            deleted: Boolean,
                            deletedUsername: Option[String])

}