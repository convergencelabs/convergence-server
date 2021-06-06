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


import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.{ActorRef, Scheduler}
import akka.http.scaladsl.marshalling.ToResponseMarshallable.apply
import akka.http.scaladsl.server.Directive.{addByNameNullaryApply, addDirectiveApply}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import com.convergencelabs.convergence.server.model.DomainId
import com.convergencelabs.convergence.server.backend.services.server.UserStoreActor
import com.convergencelabs.convergence.server.backend.services.server.UserFavoriteDomainStoreActor
import com.convergencelabs.convergence.server.model.server.user.User
import com.convergencelabs.convergence.server.security.AuthorizationProfile
import grizzled.slf4j.Logging

import scala.concurrent.{ExecutionContext, Future}

private[rest] final class CurrentUserService(convergenceUserActor: ActorRef[UserStoreActor.Message],
                                             favoriteDomainsActor: ActorRef[UserFavoriteDomainStoreActor.Message],
                                             executionContext: ExecutionContext,
                                             scheduler: Scheduler,
                                             defaultTimeout: Timeout)
  extends JsonSupport with Logging {

  import CurrentUserService._

  private[this] implicit val ec: ExecutionContext = executionContext
  private[this] implicit val s: Scheduler = scheduler
  private[this] implicit val t: Timeout = defaultTimeout

  val route: AuthorizationProfile => Route = { authProfile: AuthorizationProfile =>
    pathPrefix("user") {
      path("profile") {
        get {
          complete(getProfile(authProfile))
        } ~
          put {
            entity(as[UpdateProfileRequest]) { profile =>
              complete(updateProfile(authProfile, profile))
            }
          }
      } ~ path("bearerToken") {
        get {
          complete(getBearerToken(authProfile))
        } ~ put {
          complete(regenerateBearerToken(authProfile))
        }
      } ~ (path("password") & put) {
        entity(as[PasswordSetRequest]) { password =>
          complete(setPassword(authProfile, password))
        }
      } ~ pathPrefix("favoriteDomains") {
        (pathEnd & get) {
          complete(getFavoriteDomains(authProfile))
        } ~ path(Segment / Segment) { (namespace, domain) =>
          put {
            complete(addFavoriteDomain(authProfile, namespace, domain))
          } ~ delete {
            complete(removeFavoriteDomain(authProfile, namespace, domain))
          }
        }
      }
    }
  }

  private[this] def getBearerToken(authProfile: AuthorizationProfile): Future[RestResponse] = {
    convergenceUserActor
      .ask[UserStoreActor.GetUserBearerTokenResponse](
        UserStoreActor.GetUserBearerTokenRequest(authProfile.username, _))
      .map(_.token.fold(
        {
          case UserStoreActor.UserNotFoundError() =>
            userNotFound()
          case UserStoreActor.UnknownError() =>
            InternalServerError
        },
        token => okResponse(token)
      ))
  }

  private[this] def regenerateBearerToken(authProfile: AuthorizationProfile): Future[RestResponse] = {
    convergenceUserActor
      .ask[UserStoreActor.RegenerateUserBearerTokenResponse](
        UserStoreActor.RegenerateUserBearerTokenRequest(authProfile.username, _))
      .map(_.token.fold(
        {
          case UserStoreActor.UserNotFoundError() =>
            userNotFound()
          case UserStoreActor.UnknownError() =>
            InternalServerError
        },
        token => okResponse(token)
      ))
  }

  private[this] def setPassword(authProfile: AuthorizationProfile, request: PasswordSetRequest): Future[RestResponse] = {
    val PasswordSetRequest(password) = request
    convergenceUserActor
      .ask[UserStoreActor.SetPasswordResponse](
        UserStoreActor.SetPasswordRequest(authProfile.username, password, _))
      .map(_.response.fold(
        {
          case UserStoreActor.UserNotFoundError() =>
            userNotFound()
          case UserStoreActor.UnknownError() =>
            InternalServerError
        },
        _ => OkResponse
      ))
  }

  private[this] def getProfile(authProfile: AuthorizationProfile): Future[RestResponse] = {
    convergenceUserActor.
      ask[UserStoreActor.GetConvergenceUserResponse](
        UserStoreActor.GetConvergenceUserRequest(authProfile.username, _))
      .map(_.user.fold(
        {
          case UserStoreActor.UserNotFoundError() =>
            userNotFound()
          case UserStoreActor.UnknownError() =>
            InternalServerError
        },
        {
          case UserStoreActor.ConvergenceUserInfo(User(username, email, firstName, lastName, displayName, _), globalRole) =>
            okResponse(ConvergenceUserProfile(username, email, firstName, lastName, displayName, globalRole))
        })
      )
  }

  private[this] def updateProfile(authProfile: AuthorizationProfile, profile: UpdateProfileRequest): Future[RestResponse] = {
    val UpdateProfileRequest(email, firstName, lastName, displayName) = profile
    convergenceUserActor
      .ask[UserStoreActor.UpdateConvergenceUserProfileResponse](
        UserStoreActor.UpdateConvergenceUserProfileRequest(authProfile.username, email, firstName, lastName, displayName, _))
      .map(_.response.fold(
        {
          case UserStoreActor.UserNotFoundError() =>
            userNotFound()
          case UserStoreActor.UnknownError() =>
            InternalServerError
        },
        _ => OkResponse
      ))
  }

  private[this] def getFavoriteDomains(authProfile: AuthorizationProfile): Future[RestResponse] = {
    favoriteDomainsActor
      .ask[UserFavoriteDomainStoreActor.GetFavoritesForUserResponse](
        UserFavoriteDomainStoreActor.GetFavoritesForUserRequest(authProfile.username, _))
      .map(_.domains.fold(
        {
          case UserFavoriteDomainStoreActor.UserNotFoundError() =>
            userNotFound()
          case UserFavoriteDomainStoreActor.UnknownError() =>
            InternalServerError
        },
        { domains =>
          okResponse(domains.map(domain => DomainRestData(
            domain.displayName,
            domain.domainId.namespace,
            domain.domainId.domainId,
            domain.availability.toString,
            domain.status.toString,
            domain.statusMessage,
            None)))
        })
      )
  }

  private[this] def addFavoriteDomain(authProfile: AuthorizationProfile, namespace: String, domain: String): Future[RestResponse] = {
    favoriteDomainsActor
      .ask[UserFavoriteDomainStoreActor.AddFavoriteDomainResponse](
        UserFavoriteDomainStoreActor.AddFavoriteDomainRequest(authProfile.username, DomainId(namespace, domain), _))
      .map(_.response.fold(
        {
          case UserFavoriteDomainStoreActor.UserNotFoundError() =>
            userNotFound()
          case UserFavoriteDomainStoreActor.UnknownError() =>
            InternalServerError
        },
        _ => OkResponse
      ))
  }

  private[this] def removeFavoriteDomain(authProfile: AuthorizationProfile, namespace: String, domain: String): Future[RestResponse] = {
    favoriteDomainsActor
      .ask[UserFavoriteDomainStoreActor.RemoveFavoriteDomainResponse](
        UserFavoriteDomainStoreActor.RemoveFavoriteDomainRequest(authProfile.username, DomainId(namespace, domain), _))
      .map(_.response.fold(
        {
          case UserFavoriteDomainStoreActor.UserNotFoundError() =>
            userNotFound()
          case UserFavoriteDomainStoreActor.UnknownError() =>
            InternalServerError
        },
        _ => OkResponse
      ))
  }

  private[this] def userNotFound() =
    notFoundResponse(Some("The specified user does not exist"))
}

object CurrentUserService {

  case class BearerTokenResponse(token: String)

  case class ConvergenceUserProfile(username: String,
                                    email: String,
                                    firstName: String,
                                    lastName: String,
                                    displayName: String,
                                    serverRole: String)

  case class UpdateProfileRequest(email: String, firstName: String, lastName: String, displayName: String)

  case class PasswordSetRequest(password: String)

}
