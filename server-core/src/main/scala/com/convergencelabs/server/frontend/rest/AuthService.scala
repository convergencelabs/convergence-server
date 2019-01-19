package com.convergencelabs.server.frontend.rest

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import com.convergencelabs.server.datastore.convergence.AuthStoreActor.AuthRequest
import com.convergencelabs.server.datastore.convergence.AuthStoreActor.AuthResponse
import com.convergencelabs.server.datastore.convergence.AuthStoreActor.AuthSuccess
import com.convergencelabs.server.datastore.convergence.AuthStoreActor.GetSessionTokenExpirationRequest
import com.convergencelabs.server.datastore.convergence.AuthStoreActor.InvalidateTokenRequest
import com.convergencelabs.server.datastore.convergence.AuthStoreActor.LoginRequest
import com.convergencelabs.server.datastore.convergence.AuthStoreActor.SessionTokenExpiration
import com.convergencelabs.server.datastore.convergence.UserStore.InvalidCredentials
import com.convergencelabs.server.datastore.convergence.UserStore.LoginResult
import com.convergencelabs.server.datastore.convergence.UserStore.LoginSuccessful
import com.convergencelabs.server.datastore.convergence.UserStore.NoApiKeyForUser

import akka.actor.ActorRef
import akka.http.scaladsl.model.StatusCodes
import akka.util.Timeout

object AuthService {
  case class SessionTokenResponse(token: String, expiration: Long)
  case class ExpirationResponse(valid: Boolean, username: Option[String], delta: Option[Long])
  case class UserApiKeyResponse(apiKey: String)
  val NoApiKeyResponse: RestResponse = (StatusCodes.Unauthorized,
    ErrorResponse("no_api_key_for_user", Some("Can not login beause the user does not have an API key configured.")))
}

class AuthService(
  private[this] val executionContext: ExecutionContext,
  private[this] val authActor: ActorRef,
  private[this] val defaultTimeout: Timeout)
    extends JsonSupport {

  import AuthService._
  import akka.http.scaladsl.server.Directives._
  import akka.pattern.ask

  implicit val ec = executionContext
  implicit val t = defaultTimeout

  val route = pathPrefix("auth") {
    (path("login") & post) {
      handleWith(authRequest)
    } ~
      (path("validate") & post) {
        handleWith(tokenExprirationCheck)
      } ~
      (path("logout") & post) {
        handleWith(invalidateToken)
      } ~
      (path("apiKey") & post) {
        handleWith(login)
      }
  }

  def authRequest(req: AuthRequest): Future[RestResponse] = {
    (authActor ? req).mapTo[AuthResponse].map {
      case AuthSuccess(token, expiration) =>
        okResponse(SessionTokenResponse(token, expiration.toMillis()))
      case _ =>
        AuthFailureError
    }
  }

  def login(req: LoginRequest): Future[RestResponse] = {
    (authActor ? req).mapTo[LoginResult].map {
      case LoginSuccessful(apiKey) =>
        okResponse(UserApiKeyResponse(apiKey))
      case InvalidCredentials =>
        AuthFailureError
      case NoApiKeyForUser =>
        NoApiKeyResponse
    }
  }

  def tokenExprirationCheck(req: GetSessionTokenExpirationRequest): Future[RestResponse] = {
    (authActor ? req).mapTo[Option[SessionTokenExpiration]].map {
      case Some(SessionTokenExpiration(username, exprieDelta)) =>
        okResponse(ExpirationResponse(true, Some(username), Some(exprieDelta.toMillis())))
      case _None =>
        okResponse(ExpirationResponse(false, None, None))
    }
  }

  def invalidateToken(req: InvalidateTokenRequest): Future[RestResponse] = {
    (authActor ? req).map {
      _ => OkResponse
    }
  }
}
