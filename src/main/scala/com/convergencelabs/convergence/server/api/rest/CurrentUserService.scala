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

import akka.actor.ActorRef
import akka.http.scaladsl.marshalling.ToResponseMarshallable.apply
import akka.http.scaladsl.server.Directive.{addByNameNullaryApply, addDirectiveApply}
import akka.http.scaladsl.server.Directives.{Segment, _enhanceRouteWithConcatenation, _segmentStringToPathMatcher, as, complete, delete, entity, get, path, pathEnd, pathPrefix, put}
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import akka.util.Timeout
import com.convergencelabs.convergence.server.datastore.convergence.ConvergenceUserManagerActor._
import com.convergencelabs.convergence.server.datastore.convergence.UserFavoriteDomainStoreActor.{AddFavoriteDomain, GetFavoritesForUser, RemoveFavoriteDomain}
import com.convergencelabs.convergence.server.datastore.convergence.UserStore.User
import com.convergencelabs.convergence.server.domain.{Domain, DomainId}
import com.convergencelabs.convergence.server.security.AuthorizationProfile
import grizzled.slf4j.Logging

import scala.concurrent.{ExecutionContext, Future}

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

class CurrentUserService(private[this] val executionContext: ExecutionContext,
                         private[this] val convergenceUserActor: ActorRef,
                         private[this] val favoriteDomainsActor: ActorRef,
                         private[this] val defaultTimeout: Timeout)
  extends JsonSupport with Logging {

  import CurrentUserService._


  private[this] implicit val ec: ExecutionContext = executionContext
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
      } ~ path("apiKeys") {
        get {
          complete(okResponse("apiKey"))
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
    val message = GetUserBearerTokenRequest(authProfile.username)
    (convergenceUserActor ? message).mapTo[Option[String]].map(okResponse(_))
  }

  private[this] def regenerateBearerToken(authProfile: AuthorizationProfile): Future[RestResponse] = {
    val message = RegenerateUserBearerTokenRequest(authProfile.username)
    (convergenceUserActor ? message).mapTo[String].map(okResponse(_))
  }

  private[this] def setPassword(authProfile: AuthorizationProfile, request: PasswordSetRequest): Future[RestResponse] = {
    logger.debug(s"Received request to set the password for user: ${authProfile.username}")
    val PasswordSetRequest(password) = request
    val message = SetPasswordRequest(authProfile.username, password)
    (convergenceUserActor ? message) map { _ => OkResponse }
  }

  private[this] def getProfile(authProfile: AuthorizationProfile): Future[RestResponse] = {
    val message = GetConvergenceUser(authProfile.username)
    (convergenceUserActor ? message).mapTo[Option[ConvergenceUserInfo]].map {
      case Some(ConvergenceUserInfo(User(username, email, firstName, lastName, displayName, _), globalRole)) =>
        okResponse(ConvergenceUserProfile(username, email, firstName, lastName, displayName, globalRole))
      case None =>
        notFoundResponse()
    }
  }

  private[this] def updateProfile(authProfile: AuthorizationProfile, profile: UpdateProfileRequest): Future[RestResponse] = {
    val UpdateProfileRequest(email, firstName, lastName, displayName) = profile
    val message = UpdateConvergenceUserProfileRequest(authProfile.username, email, firstName, lastName, displayName)
    (convergenceUserActor ? message) map { _ => OkResponse }
  }

  private[this] def getFavoriteDomains(authProfile: AuthorizationProfile): Future[RestResponse] = {
    val message = GetFavoritesForUser(authProfile.username)
    (favoriteDomainsActor ? message).mapTo[List[Domain]] map { domains =>
      okResponse(domains.map(domain => DomainRestData(
        domain.displayName,
        domain.domainFqn.namespace,
        domain.domainFqn.domainId,
        domain.status.toString)))
    }
  }

  private[this] def addFavoriteDomain(authProfile: AuthorizationProfile, namespace: String, domain: String): Future[RestResponse] = {
    val message = AddFavoriteDomain(authProfile.username, DomainId(namespace, domain))
    (favoriteDomainsActor ? message).mapTo[Unit] map {
      okResponse(_)
    }
  }

  private[this] def removeFavoriteDomain(authProfile: AuthorizationProfile, namespace: String, domain: String): Future[RestResponse] = {
    val message = RemoveFavoriteDomain(authProfile.username, DomainId(namespace, domain))
    (favoriteDomainsActor ? message).mapTo[Unit] map {
      okResponse(_)
    }
  }
}
