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

package com.convergencelabs.convergence.server.api.rest

import java.time.Instant

import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.{ActorRef, Scheduler}
import akka.http.scaladsl.marshalling.ToResponseMarshallable.apply
import akka.http.scaladsl.server.Directive.{addByNameNullaryApply, addDirectiveApply}
import akka.http.scaladsl.server.Directives.{Segment, _enhanceRouteWithConcatenation, _segmentStringToPathMatcher, _string2NR, as, authorize, complete, delete, entity, get, parameters, path, pathEnd, pathPrefix, post, put}
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import com.convergencelabs.convergence.server.backend.services.server.UserStoreActor._
import com.convergencelabs.convergence.server.model.server.user.User
import com.convergencelabs.convergence.server.security.{AuthorizationProfile, Permissions}
import com.convergencelabs.convergence.server.util.{QueryLimit, QueryOffset}

import scala.concurrent.{ExecutionContext, Future}

private[rest] class UserService(userManagerActor: ActorRef[Message],
                                scheduler: Scheduler,
                                executionContext: ExecutionContext,
                                defaultTimeout: Timeout) extends JsonSupport {

  import UserService._

  private[this] implicit val ec: ExecutionContext = executionContext
  private[this] implicit val t: Timeout = defaultTimeout
  private[this] implicit val s: Scheduler = scheduler

  val route: AuthorizationProfile => Route = { authProfile: AuthorizationProfile =>
    pathPrefix("users") {
      pathEnd {
        get {
          parameters("filter".?, "limit".as[Long].?, "offset".as[Long].?) { (filter, limit, offset) =>
            complete(getUsers(filter, limit, offset, authProfile))
          }
        } ~ post {
          authorize(canManageUsers(authProfile)) {
            entity(as[CreateUserRequestData]) { request =>
              complete(createConvergenceUser(request))
            }
          }
        }
      } ~ pathPrefix(Segment) { username =>
        pathEnd {
          get {
            complete(getUser(username, authProfile))
          } ~ delete {
            authorize(canManageUsers(authProfile)) {
              complete(deleteConvergenceUserRequest(username, authProfile))
            }
          } ~ put {
            authorize(canManageUsers(authProfile)) {
              entity(as[UpdateUserData]) { request =>
                complete(updateUser(username, request, authProfile))
              }
            }
          }
        } ~ path("password") {
          post {
            authorize(canManageUsers(authProfile)) {
              entity(as[PasswordData]) { request =>
                complete(setUserPassword(username, request))
              }
            }
          }
        }
      }
    }
  }

  private[this] def getUsers(filter: Option[String], limit: Option[Long], offset: Option[Long], authorizationProfile: AuthorizationProfile): Future[RestResponse] = {
    userManagerActor
      .ask[GetConvergenceUsersResponse](GetConvergenceUsersRequest(filter, QueryOffset(offset), QueryLimit(limit), _))
      .map(_.users.fold(
        {
          case UnknownError() =>
            InternalServerError
        },
        { users =>
          val publicUsers = users.map(mapUser(_, authorizationProfile))
          okResponse(publicUsers)
        })
      )
  }

  private[this] def getUser(username: String, authorizationProfile: AuthorizationProfile): Future[RestResponse] = {
    userManagerActor
      .ask[GetConvergenceUserResponse](GetConvergenceUserRequest(username, _))
      .map(_.user.fold(
        {
          case UserNotFoundError() =>
            NotFoundResponse
          case UnknownError() =>
            InternalServerError
        },
        { user =>
          val publicUser = mapUser(user, authorizationProfile)
          okResponse(publicUser)
        })
      )
  }

  private[this] def createConvergenceUser(createRequest: CreateUserRequestData): Future[RestResponse] = {
    val CreateUserRequestData(username, firstName, lastName, displayName, email, serverRole, password) = createRequest
    val fName = firstName.getOrElse("")
    val lName = lastName.getOrElse("")
    userManagerActor
      .ask[CreateConvergenceUserResponse](CreateConvergenceUserRequest(username, email, fName, lName, displayName, password, serverRole, _))
      .map(_.response.fold(
        {
          case InvalidValueError(field) =>
            badRequest(s"An invalid value for '$field' was provided")
          case UserAlreadyExistsError(field) =>
            duplicateResponse(field)
          case UnknownError() =>
            InternalServerError
        },
        { _ =>
          CreatedResponse
        })
      )
  }

  private[this] def deleteConvergenceUserRequest(username: String, authProfile: AuthorizationProfile): Future[RestResponse] = {
    if (authProfile.username == username) {
      Future.successful(forbiddenResponse(Some("You can not delete your own user.")))
    } else {
      userManagerActor
        .ask[DeleteConvergenceUserResponse](DeleteConvergenceUserRequest(username, _))
        .map(_.response.fold(
          {
            case UserNotFoundError() =>
              NotFoundResponse
            case UnknownError() =>
              InternalServerError
          },
          { _ =>
            DeletedResponse
          })
        )
    }
  }

  private[this] def updateUser(username: String, updateData: UpdateUserData, authProfile: AuthorizationProfile): Future[RestResponse] = {
    if (username == authProfile.username && !authProfile.hasServerRole(updateData.serverRole)) {
      Future.successful(forbiddenResponse(Some("You can not change your own server role.")))
    } else {
      val UpdateUserData(firstName, lastName, displayName, email, serverRole) = updateData
      userManagerActor
        .ask[UpdateConvergenceUserResponse](UpdateConvergenceUserRequest(username, email, firstName, lastName, displayName, serverRole, _))
        .map(_.response.fold(
          {
            case UserNotFoundError() =>
              NotFoundResponse
            case UnknownError() =>
              InternalServerError
          },
          { _ =>
            OkResponse
          })
        )
    }
  }

  private[this] def setUserPassword(username: String, passwordData: PasswordData): Future[RestResponse] = {
    val PasswordData(password) = passwordData
    userManagerActor.ask[SetPasswordResponse](SetPasswordRequest(username, password, _))
      .map(_.response.fold(
        {
          case UserNotFoundError() =>
            NotFoundResponse
          case UnknownError() =>
            InternalServerError
        },
        { _ =>
          OkResponse
        })
      )
  }

  private[this] def mapUser(user: ConvergenceUserInfo, authProfile: AuthorizationProfile): UserData = {
    val ConvergenceUserInfo(User(username, email, firstName, lastName, displayName, lastLogin), globalRole) = user
    if (canManageUsers(authProfile)) {
      UserData(username, firstName, lastName, displayName, Some(email), lastLogin, globalRole)
    } else {
      UserData(username, firstName, lastName, displayName, None, None, globalRole)
    }
  }

  private[this] def canManageUsers(authProfile: AuthorizationProfile): Boolean = {
    authProfile.hasGlobalPermission(Permissions.Server.ManageUsers)
  }
}

private[rest] object UserService {

  case class CreateUserRequestData(username: String, firstName: Option[String], lastName: Option[String], displayName: String, email: String, serverRole: String, password: String)

  case class UserPublicData(username: String, displayName: String)

  case class UserData(username: String, firstName: String, lastName: String, displayName: String, email: Option[String], lastLogin: Option[Instant], serverRole: String)

  case class PasswordData(password: String)

  case class UpdateUserData(firstName: String, lastName: String, displayName: String, email: String, serverRole: String)

}
