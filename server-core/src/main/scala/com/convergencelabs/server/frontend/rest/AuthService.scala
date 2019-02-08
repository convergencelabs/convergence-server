package com.convergencelabs.server.frontend.rest

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import com.convergencelabs.server.datastore.convergence.AuthenticationActor.AuthRequest
import com.convergencelabs.server.datastore.convergence.AuthenticationActor.AuthResponse
import com.convergencelabs.server.datastore.convergence.AuthenticationActor.AuthSuccess
import com.convergencelabs.server.datastore.convergence.AuthenticationActor.GetSessionTokenExpirationRequest
import com.convergencelabs.server.datastore.convergence.AuthenticationActor.InvalidateTokenRequest
import com.convergencelabs.server.datastore.convergence.AuthenticationActor.LoginRequest
import com.convergencelabs.server.datastore.convergence.AuthenticationActor.SessionTokenExpiration

import akka.actor.ActorRef
import akka.http.scaladsl.model.StatusCodes
import akka.util.Timeout

object AuthService {
  case class SessionTokenResponse(token: String, expiration: Long)
  case class ExpirationResponse(valid: Boolean, username: Option[String], delta: Option[Long])
  case class BearerTokenResponse(token: String)
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
    (authActor ? req).mapTo[Option[String]].map {
      case Some(token) =>
        okResponse(BearerTokenResponse(token))
      case None =>
        AuthFailureError
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
