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

import akka.actor.typed.{ActorRef, ActorSystem}
import akka.actor.typed.scaladsl.AskPattern._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import com.convergencelabs.convergence.server.datastore.convergence.AuthenticationActor

import scala.concurrent.{ExecutionContext, Future}

class AuthService(private[this] val authActor: ActorRef[AuthenticationActor.Message],
                  private[this] val system: ActorSystem[_],
                  private[this] val executionContext: ExecutionContext,
                  private[this] val defaultTimeout: Timeout)
  extends JsonSupport {

  private[this] implicit val ec: ExecutionContext = executionContext
  private[this] implicit val t: Timeout = defaultTimeout
  private[this] implicit val s: ActorSystem[_] = system

  import AuthService._

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

    authActor.ask[AuthenticationActor.AuthResponse](AuthenticationActor.AuthRequest(username, password, _)) map {
      case AuthenticationActor.AuthSuccess(token, expiration) =>
        okResponse(SessionTokenResponse(token, expiration.toMillis))
      case _ =>
        AuthFailureError
    }
  }

  private[this] def bearerToken(req: BearerTokenRequestData): Future[RestResponse] = {
    val BearerTokenRequestData(username, password) = req
    authActor.ask[AuthenticationActor.LoginResponse](AuthenticationActor.LoginRequest(username, password, _)).map {
      case AuthenticationActor.LoginSuccess(Some(token)) =>
        okResponse(BearerTokenResponse(token))
      case _ =>
        AuthFailureError
    }
  }

  private[this] def validate(req: ValidateRequestData): Future[RestResponse] = {
    val ValidateRequestData(token) = req
    authActor.ask[AuthenticationActor.GetSessionTokenExpirationResponse](AuthenticationActor.GetSessionTokenExpirationRequest(token, _))
      .map {
        case AuthenticationActor.GetSessionTokenExpirationSuccess(Some(AuthenticationActor.SessionTokenExpiration(username, expireDelta))) =>
          okResponse(ExpirationResponse(valid = true, Some(username), Some(expireDelta.toMillis)))
        case _ =>
          okResponse(ExpirationResponse(valid = false, None, None))
      }
  }

  private[this] def logout(req: LogoutRequestData): Future[RestResponse] = {
    val LogoutRequestData(token) = req

    authActor.ask[AuthenticationActor.InvalidateSessionTokenResponse](AuthenticationActor.InvalidateSessionTokenRequest(token, _)).map {
      case AuthenticationActor.RequestSuccess() => OkResponse
      case _ => InternalServerError
    }
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
