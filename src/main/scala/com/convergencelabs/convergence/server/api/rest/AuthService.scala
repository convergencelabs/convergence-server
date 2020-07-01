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
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import com.convergencelabs.convergence.server.backend.services.server.AuthenticationActor

import scala.concurrent.{ExecutionContext, Future}

/**
 * The [[AuthService]] Handles authentication related service requests from the
 * Convergence HTTP API. This includes username / password authentication as
 * well as the authentication via tokens.
 *
 * @param authActor        The back end [[AuthenticationActor]] that will process the
 *                         authentication requests.
 * @param scheduler        The Scheduler to user for for async communication with the
 *                         backend actors.
 * @param executionContext The execution context to use for Future processing.
 * @param defaultTimeout   The default timeout the service will use for async requests
 *                         to the backend.
 */
class AuthService(authActor: ActorRef[AuthenticationActor.Message],
                  scheduler: Scheduler,
                  executionContext: ExecutionContext,
                  defaultTimeout: Timeout)
  extends JsonSupport {

  import AuthService._

  private[this] implicit val ec: ExecutionContext = executionContext
  private[this] implicit val t: Timeout = defaultTimeout
  private[this] implicit val s: Scheduler = scheduler

  val route: Route = pathPrefix("auth") {
    (path("login") & post) {
      handleWith(login)
    } ~ (path("validate") & post) {
      handleWith(validate)
    } ~ (path("logout") & post) {
      handleWith(logout)
    } ~ (path("bearerToken") & post) {
      handleWith(bearerToken)
    }
  }

  private[this] def login(req: LoginRequestData): Future[RestResponse] = {
    val LoginRequestData(username, password) = req
    authActor.ask[AuthenticationActor.AuthResponse](AuthenticationActor.AuthRequest(username, password, _))
      .map(_.data.fold({
        case AuthenticationActor.AuthenticationFailed() =>
          AuthFailureError
        case AuthenticationActor.UnknownError() =>
          InternalServerError
      }, {
        case AuthenticationActor.AuthData(token, expiration) =>
          okResponse(SessionTokenResponse(token, expiration.toMillis))
      }))
  }

  private[this] def bearerToken(req: BearerTokenRequestData): Future[RestResponse] = {
    val BearerTokenRequestData(username, password) = req
    authActor.ask[AuthenticationActor.LoginResponse](AuthenticationActor.LoginRequest(username, password, _))
      .map(_.bearerToken.fold({
        case AuthenticationActor.LoginFailed() =>
          AuthFailureError
        case AuthenticationActor.UnknownError() =>
          InternalServerError
      }, { bearerToken =>
        okResponse(BearerTokenResponse(bearerToken))
      }))
  }

  private[this] def validate(req: ValidateRequestData): Future[RestResponse] = {
    val ValidateRequestData(token) = req
    authActor.ask[AuthenticationActor.GetSessionTokenExpirationResponse](AuthenticationActor.GetSessionTokenExpirationRequest(token, _))
      .map(_.expiration.fold({
        case AuthenticationActor.TokenNotFoundError() =>
          okResponse(ExpirationResponse(valid = false, None, None))
        case AuthenticationActor.UnknownError() =>
          InternalServerError
      }, {
        case AuthenticationActor.SessionTokenExpiration(username, expireDelta) =>
          okResponse(ExpirationResponse(valid = true, Some(username), Some(expireDelta.toMillis)))
      }))
  }

  private[this] def logout(req: LogoutRequestData): Future[RestResponse] = {
    val LogoutRequestData(token) = req
    authActor.ask[AuthenticationActor.InvalidateSessionTokenResponse](AuthenticationActor.InvalidateSessionTokenRequest(token, _))
      .map(_.response.fold(
        {
          case AuthenticationActor.UnknownError() =>
            InternalServerError
        },
        _ => OkResponse
      ))
  }
}

object AuthService {

  case class SessionTokenResponse(token: String, expiresIn: Long)

  case class ExpirationResponse(valid: Boolean, username: Option[String], expiresIn: Option[Long])

  case class BearerTokenResponse(token: String)

  case class ValidateRequestData(token: String)

  case class LogoutRequestData(token: String)

  case class LoginRequestData(username: String, password: String)

  case class BearerTokenRequestData(username: String, password: String)

}
