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
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.http.scaladsl.marshalling.ToResponseMarshallable.apply
import akka.http.scaladsl.server.Directive.{addByNameNullaryApply, addDirectiveApply}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import com.convergencelabs.convergence.server.datastore.convergence.ConvergenceUserManagerActor.{RegenerateUserBearerTokenResponse, _}
import com.convergencelabs.convergence.server.datastore.convergence.UserFavoriteDomainStoreActor.{GetFavoritesForUserResponse, _}
import com.convergencelabs.convergence.server.datastore.convergence.UserStore.User
import com.convergencelabs.convergence.server.datastore.convergence.{ConvergenceUserManagerActor, UserFavoriteDomainStoreActor}
import com.convergencelabs.convergence.server.domain.DomainId
import com.convergencelabs.convergence.server.security.AuthorizationProfile
import grizzled.slf4j.Logging

import scala.concurrent.{ExecutionContext, Future}

private[rest] class CurrentUserService(private[this] val convergenceUserActor: ActorRef[ConvergenceUserManagerActor.Message],
                         private[this] val favoriteDomainsActor: ActorRef[UserFavoriteDomainStoreActor.Message],
                         private[this] val executionContext: ExecutionContext,
                         private[this] val system: ActorSystem[_],
                         private[this] val defaultTimeout: Timeout)
  extends JsonSupport with Logging {

  import CurrentUserService._

  private[this] implicit val ec: ExecutionContext = executionContext
  private[this] implicit val t: Timeout = defaultTimeout
  private[this] implicit val s: ActorSystem[_] = system

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
    convergenceUserActor.ask[GetUserBearerTokenResponse](GetUserBearerTokenRequest(authProfile.username, _)).map {
      case GetUserBearerTokenSuccess(token) => okResponse(token)
      case _ => InternalServerError
    }
  }

  private[this] def regenerateBearerToken(authProfile: AuthorizationProfile): Future[RestResponse] = {
    convergenceUserActor.ask[RegenerateUserBearerTokenResponse](RegenerateUserBearerTokenRequest(authProfile.username, _)).map {
      case RegenerateUserBearerTokenSuccess(token) => okResponse(token)
      case _ => InternalServerError
    }
  }

  private[this] def setPassword(authProfile: AuthorizationProfile, request: PasswordSetRequest): Future[RestResponse] = {
    val PasswordSetRequest(password) = request
    convergenceUserActor.ask[ConvergenceUserManagerActor.SetPasswordResponse](SetPasswordRequest(authProfile.username, password, _)) map {
      case ConvergenceUserManagerActor.RequestSuccess() => OkResponse
      case _ => InternalServerError
    }
  }

  private[this] def getProfile(authProfile: AuthorizationProfile): Future[RestResponse] = {
    convergenceUserActor.ask[GetConvergenceUserResponse](GetConvergenceUserRequest(authProfile.username, _)).map {
      case GetConvergenceUserSuccess(Some(ConvergenceUserInfo(User(username, email, firstName, lastName, displayName, _), globalRole))) =>
        okResponse(ConvergenceUserProfile(username, email, firstName, lastName, displayName, globalRole))
      case GetConvergenceUserSuccess(None) =>
        notFoundResponse()
      case _ =>
        InternalServerError
    }
  }

  private[this] def updateProfile(authProfile: AuthorizationProfile, profile: UpdateProfileRequest): Future[RestResponse] = {
    val UpdateProfileRequest(email, firstName, lastName, displayName) = profile
    convergenceUserActor.ask[UpdateConvergenceUserProfileResponse](
      UpdateConvergenceUserProfileRequest(authProfile.username, email, firstName, lastName, displayName, _)).map {
      case ConvergenceUserManagerActor.RequestSuccess() =>
        OkResponse
      case _ =>
        InternalServerError
    }
  }

  private[this] def getFavoriteDomains(authProfile: AuthorizationProfile): Future[RestResponse] = {
    favoriteDomainsActor.ask[GetFavoritesForUserResponse](GetFavoritesForUserRequest(authProfile.username, _)).map {
      case GetFavoritesForUserSuccess(domains) =>
        okResponse(domains.map(domain => DomainRestData(
          domain.displayName,
          domain.domainFqn.namespace,
          domain.domainFqn.domainId,
          domain.status.toString)))
      case _ =>
        InternalServerError
    }
  }

  private[this] def addFavoriteDomain(authProfile: AuthorizationProfile, namespace: String, domain: String): Future[RestResponse] = {
    favoriteDomainsActor.ask[AddFavoriteDomainResponse](AddFavoriteDomainRequest(authProfile.username, DomainId(namespace, domain), _)).map {
      case UserFavoriteDomainStoreActor.RequestSuccess() =>
        OkResponse
      case _ =>
        InternalServerError
    }
  }

  private[this] def removeFavoriteDomain(authProfile: AuthorizationProfile, namespace: String, domain: String): Future[RestResponse] = {
    favoriteDomainsActor.ask[RemoveFavoriteDomainResponse](RemoveFavoriteDomainRequest(authProfile.username, DomainId(namespace, domain), _)).map {
      case UserFavoriteDomainStoreActor.RequestSuccess() =>
        OkResponse
      case _ =>
        InternalServerError
    }
  }
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