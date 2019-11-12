/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

package com.convergencelabs.server.api.rest

import akka.actor.ActorRef
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import com.convergencelabs.server.datastore.convergence.AuthenticationActor._

import scala.concurrent.{ExecutionContext, Future}

object AuthService {
  case class SessionTokenResponse(token: String, expiresIn: Long)
  case class ExpirationResponse(valid: Boolean, username: Option[String], expiresIn: Option[Long])
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

  implicit val ec: ExecutionContext = executionContext
  implicit val t: Timeout = defaultTimeout

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

  def authRequest(req: AuthRequest): Future[RestResponse] = {
    (authActor ? req).mapTo[AuthResponse].map {
      case AuthSuccess(token, expiration) =>
        okResponse(SessionTokenResponse(token, expiration.toMillis))
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

  def tokenExpirationCheck(req: GetSessionTokenExpirationRequest): Future[RestResponse] = {
    (authActor ? req).mapTo[Option[SessionTokenExpiration]].map {
      case Some(SessionTokenExpiration(username, expireDelta)) =>
        okResponse(ExpirationResponse(valid = true, Some(username), Some(expireDelta.toMillis)))
      case None =>
        okResponse(ExpirationResponse(valid = false, None, None))
    }
  }

  def invalidateToken(req: InvalidateTokenRequest): Future[RestResponse] = {
    (authActor ? req).map(_ => OkResponse)
  }
}
