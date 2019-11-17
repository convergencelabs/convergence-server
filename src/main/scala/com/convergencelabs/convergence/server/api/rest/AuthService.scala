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
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import akka.util.Timeout
import com.convergencelabs.convergence.server.datastore.convergence.AuthenticationActor._

import scala.concurrent.{ExecutionContext, Future}

object AuthService {

  case class SessionTokenResponse(token: String, expiresIn: Long)

  case class ExpirationResponse(valid: Boolean, username: Option[String], expiresIn: Option[Long])

  case class BearerTokenResponse(token: String)

}

class AuthService(private[this] val executionContext: ExecutionContext,
                  private[this] val authActor: ActorRef,
                  private[this] val defaultTimeout: Timeout)
  extends JsonSupport {

  import AuthService._

  private[this] implicit val ec: ExecutionContext = executionContext
  private[this] implicit val t: Timeout = defaultTimeout

  val route: Route = pathPrefix("auth") {
    (path("login") & post) {
      handleWith(authRequest)
    } ~
      (path("validate") & post) {
        handleWith(tokenExpirationCheck)
      } ~
      (path("logout") & post) {
        handleWith(invalidateToken)
      } ~
      (path("apiKey") & post) {
        handleWith(login)
      }
  }

  private[this] def authRequest(req: AuthRequest): Future[RestResponse] = {
    (authActor ? req).mapTo[AuthResponse].map {
      case AuthSuccess(token, expiration) =>
        okResponse(SessionTokenResponse(token, expiration.toMillis))
      case _ =>
        AuthFailureError
    }
  }

  private[this] def login(req: LoginRequest): Future[RestResponse] = {
    (authActor ? req).mapTo[Option[String]].map {
      case Some(token) =>
        okResponse(BearerTokenResponse(token))
      case None =>
        AuthFailureError
    }
  }

  private[this] def tokenExpirationCheck(req: GetSessionTokenExpirationRequest): Future[RestResponse] = {
    (authActor ? req).mapTo[Option[SessionTokenExpiration]].map {
      case Some(SessionTokenExpiration(username, expireDelta)) =>
        okResponse(ExpirationResponse(valid = true, Some(username), Some(expireDelta.toMillis)))
      case None =>
        okResponse(ExpirationResponse(valid = false, None, None))
    }
  }

  private[this] def invalidateToken(req: InvalidateTokenRequest): Future[RestResponse] = {
    (authActor ? req).map(_ => OkResponse)
  }
}
