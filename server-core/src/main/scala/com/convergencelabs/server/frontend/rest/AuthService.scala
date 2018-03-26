package com.convergencelabs.server.frontend.rest

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import com.convergencelabs.server.datastore.AuthStoreActor.AuthRequest
import com.convergencelabs.server.datastore.AuthStoreActor.AuthSuccess
import com.convergencelabs.server.datastore.AuthStoreActor.InvalidateTokenRequest
import com.convergencelabs.server.datastore.AuthStoreActor.SessionTokenExpiration
import com.convergencelabs.server.datastore.AuthStoreActor.GetSessionTokenExpirationRequest

import akka.actor.ActorRef
import akka.http.scaladsl.model.StatusCodes
import akka.util.Timeout
import akka.pattern.ask
import com.convergencelabs.server.datastore.AuthStoreActor.LoginRequest
import com.convergencelabs.server.datastore.AuthStoreActor.AuthResponse
import com.convergencelabs.server.datastore.UserStore.LoginSuccessful
import com.convergencelabs.server.datastore.UserStore.LoginResult
import com.convergencelabs.server.datastore.UserStore.InvalidCredentials
import com.convergencelabs.server.datastore.UserStore.NoApiKeyForUser

object AuthService {
  case class SessionTokenResponse(token: String, expiration: Long) extends AbstractSuccessResponse
  case class ExpirationResponse(valid: Boolean, username: Option[String], delta: Option[Long]) extends AbstractSuccessResponse
  case class UserApiKeyResponse(apiKey: String) extends AbstractSuccessResponse
  val NoApiKeyResponse: RestResponse = (StatusCodes.Unauthorized, 
      ErrorResponse("no_api_key_for_user", Some("Can not login beause the user does not have an API key configured.")))
}

class AuthService(
  private[this] val executionContext: ExecutionContext,
  private[this] val authActor: ActorRef,
  private[this] val defaultTimeout: Timeout)
    extends JsonSupport {

  import akka.http.scaladsl.server.Directives._
  import AuthService._

  implicit val ec = executionContext
  implicit val t = defaultTimeout

  val route = pathPrefix("auth") {
    pathEnd {
      post {
        handleWith(authRequest)
      }
    } ~ pathPrefix("check") {
      pathEnd {
        post {
          handleWith(tokenExprirationCheck)
        }
      }
    } ~ pathPrefix("invalidate") {
      pathEnd {
        post {
          handleWith(invalidateToken)
        }
      }
    }
  } ~ path("login") {
    post {
      handleWith(login)
    }
  }

  def authRequest(req: AuthRequest): Future[RestResponse] = {
    (authActor ? req).mapTo[AuthResponse].map {
      case AuthSuccess(token, expiration) =>
        (StatusCodes.OK, SessionTokenResponse(token, expiration.toMillis()))
      case _ =>
        AuthFailureError
    }
  }

  def login(req: LoginRequest): Future[RestResponse] = {
    (authActor ? req).mapTo[LoginResult].map {
      case LoginSuccessful(apiKey) =>
        (StatusCodes.OK, UserApiKeyResponse(apiKey))
      case InvalidCredentials =>
        AuthFailureError
      case NoApiKeyForUser =>
        NoApiKeyResponse
    }
  }

  def tokenExprirationCheck(req: GetSessionTokenExpirationRequest): Future[RestResponse] = {
    (authActor ? req).mapTo[Option[SessionTokenExpiration]].map {
      case Some(SessionTokenExpiration(username, exprieDelta)) =>
        (StatusCodes.OK, ExpirationResponse(true, Some(username), Some(exprieDelta.toMillis())))
      case _None =>
        (StatusCodes.OK, ExpirationResponse(false, None, None))
    }
  }

  def invalidateToken(req: InvalidateTokenRequest): Future[RestResponse] = {
    (authActor ? req).map {
      _ => OkResponse
    }
  }
}
