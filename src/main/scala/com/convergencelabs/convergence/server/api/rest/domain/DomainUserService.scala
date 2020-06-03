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
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.http.scaladsl.marshalling.ToResponseMarshallable.apply
import akka.http.scaladsl.server.Directive._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import com.convergencelabs.convergence.server.api.rest._
import com.convergencelabs.convergence.server.datastore.domain.UserGroupStoreActor.{RequestFailure, RequestSuccess}
import com.convergencelabs.convergence.server.datastore.domain.UserStoreActor
import com.convergencelabs.convergence.server.datastore.domain.UserStoreActor._
import com.convergencelabs.convergence.server.domain.rest.DomainRestActor
import com.convergencelabs.convergence.server.domain.rest.DomainRestActor.DomainRestMessage
import com.convergencelabs.convergence.server.domain.{DomainId, DomainUser, DomainUserId, DomainUserType}
import com.convergencelabs.convergence.server.security.AuthorizationProfile

import scala.concurrent.{ExecutionContext, Future}


class DomainUserService(private[this] val domainRestActor: ActorRef[DomainRestActor.Message],
                        private[this] val system: ActorSystem[_],
                        private[this] val executionContext: ExecutionContext,
                        private[this] val timeout: Timeout)
  extends AbstractDomainRestService(system, executionContext, timeout) {

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
    } ~ pathPrefix("user-lookup") {
      pathEnd {
        post {
          entity(as[UserLookupRequest]) { request =>
            complete(findUser(domain, request))
          }
        }
      }
    }
  }

  private[this] def getAllUsersRequest(domain: DomainId, filter: Option[String], offset: Option[Int], limit: Option[Int]): Future[RestResponse] = {
    domainRestActor.ask[GetUsersResponse](r => DomainRestMessage(domain, GetUsersRequest(filter, offset, limit, r))).flatMap {
      case GetUsersSuccess(users) =>
        // FIXME Paged Data
        Future.successful(okResponse(users.data.map(toUserData)))
      case UserStoreActor.RequestFailure(cause) =>
        Future.failed(cause)
    }
  }

  private[this] def findUser(domain: DomainId, request: UserLookupRequest): Future[RestResponse] = {
    val UserLookupRequest(filter, excludes, offset, limit) = request
    val excludedUsers = excludes.map(_.map(DomainUserId(DomainUserType.Normal, _)))
    domainRestActor.ask[FindUsersResponse](r => DomainRestMessage(domain, FindUsersRequest(filter, excludedUsers, offset, limit, r))).flatMap {
      case FindUsersSuccess(users) =>
        // FIXME paged data.
        Future.successful(okResponse(users.data.map(toUserData)))
      case UserStoreActor.RequestFailure(cause) =>
        Future.failed(cause)
    }
  }

  private[this] def createUserRequest(createRequest: CreateUserRequestData, domain: DomainId): Future[RestResponse] = {
    val CreateUserRequestData(username, firstName, lastName, displayName, email, password) = createRequest
    domainRestActor.ask[CreateUserResponse](r =>
      DomainRestMessage(domain, CreateUserRequest(username, firstName, lastName, displayName, email, password, r))).flatMap {
      case UserStoreActor.CreateUserSuccess(_) =>
        Future.successful(CreatedResponse)
      case UserStoreActor.RequestFailure(cause) =>
        Future.failed(cause)
    }
  }

  private[this] def updateUserRequest(username: String, updateRequest: UpdateUserRequestData, domain: DomainId): Future[RestResponse] = {
    val UpdateUserRequestData(firstName, lastName, displayName, email, disabled) = updateRequest
    domainRestActor.ask[UpdateUserResponse](r =>
      DomainRestMessage(domain, UpdateUserRequest(username, firstName, lastName, displayName, email, disabled, r))).flatMap {
      case UserStoreActor.RequestSuccess() =>
        Future.successful(OkResponse)
      case UserStoreActor.RequestFailure(cause) =>
        Future.failed(cause)
    }
  }

  private[this] def setPasswordRequest(uid: String, setPasswordRequest: SetPasswordRequestData, domain: DomainId): Future[RestResponse] = {
    domainRestActor.ask[SetPasswordResponse](r =>
      DomainRestMessage(domain, SetPasswordRequest(uid, setPasswordRequest.password, r))).flatMap {
      case UserStoreActor.RequestSuccess() =>
        Future.successful(OkResponse)
      case UserStoreActor.RequestFailure(cause) =>
        Future.failed(cause)
    }
  }

  private[this] def getUserByUsername(username: String, domain: DomainId): Future[RestResponse] = {
    val userId = DomainUserId(DomainUserType.Normal, username)
    domainRestActor.ask[GetUserResponse](r =>
      DomainRestMessage(domain, GetUserRequest(userId, r))).flatMap {
      case UserStoreActor.GetUserSuccess(Some(user)) =>
        Future.successful(okResponse(toUserData(user)))
      case UserStoreActor.GetUserSuccess(None) =>
        Future.successful(notFoundResponse())
      case UserStoreActor.RequestFailure(cause) =>
        Future.failed(cause)
    }
  }

  private[this] def deleteUser(uid: String, domain: DomainId): Future[RestResponse] = {
    domainRestActor.ask[DeleteUserResponse](r =>
      DomainRestMessage(domain, DeleteUserRequest(uid, r))).flatMap {
      case UserStoreActor.RequestSuccess() =>
        Future.successful(DeletedResponse)
      case UserStoreActor.RequestFailure(cause) =>
        Future.failed(cause)
    }
  }

  private[this] def toUserData(user: DomainUser): DomainUserData = {
    val DomainUser(userType, username, firstName, lastName, displayName, email, lastLogin, disabled, deleted, deletedUsername) = user
    val userId = DomainUserId(userType, username)
    DomainUserData(userId.username, firstName, lastName, displayName, email, lastLogin, disabled, deleted, deletedUsername)
  }
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

  case class UserLookupRequest(filter: String, exclude: Option[List[String]], offset: Option[Int], limit: Option[Int])

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