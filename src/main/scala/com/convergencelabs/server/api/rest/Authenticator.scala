/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

package com.convergencelabs.server.api.rest

import akka.actor.ActorRef
import akka.http.scaladsl.marshalling.ToResponseMarshallable.apply
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.headers.{Authorization, GenericHttpCredentials}
import akka.http.scaladsl.server.Directive1
import akka.http.scaladsl.server.StandardRoute.toDirective
import akka.http.scaladsl.server.directives.BasicDirectives.provide
import akka.http.scaladsl.server.directives.FutureDirectives.onSuccess
import akka.http.scaladsl.server.directives.OnSuccessMagnet.apply
import akka.http.scaladsl.server.directives.RouteDirectives.complete
import akka.util.Timeout
import com.convergencelabs.server.datastore.convergence.AuthenticationActor.{ValidateSessionTokenRequest, ValidateUserApiKeyRequest, ValidateUserBearerTokenRequest}
import com.convergencelabs.server.security.AuthorizationProfile

import scala.concurrent.{ExecutionContext, Future}

/**
 * This class provides a helper directive to authenticate users and validate
 * tokens
 */
class Authenticator(
                     private[this] val authActor: ActorRef,
                     private[this] val timeout: Timeout,
                     private[this] val executionContext: ExecutionContext)
  extends JsonSupport {

  import akka.pattern.ask

  private[this] implicit val ec: ExecutionContext = executionContext
  private[this] implicit val t: Timeout = timeout

  private[this] val SessionTokenScheme = "SessionToken"
  private[this] val BearerTokenScheme = "BearerToken"
  private[this] val UserApiKeyScheme = "UserApiKey"

  def requireAuthenticatedUser(request: HttpRequest): Directive1[AuthorizationProfile] = {
    request.header[Authorization] match {
      case Some(Authorization(GenericHttpCredentials(SessionTokenScheme, token, _))) if token != "" && Option(token).isDefined =>
        validate(validateSessionToken(token))

      case Some(Authorization(GenericHttpCredentials(SessionTokenScheme, _, params))) if params.keySet == Set("") =>
        validate(validateSessionToken(params("")))

      case Some(Authorization(GenericHttpCredentials(BearerTokenScheme, token, _))) if token != "" && Option(token).isDefined =>
        validate(validateUserBearerToken(token))

      case Some(Authorization(GenericHttpCredentials(BearerTokenScheme, _, params))) if params.keySet == Set("") =>
        validate(validateUserBearerToken(params("")))

      case Some(Authorization(GenericHttpCredentials(UserApiKeyScheme, token, _))) if token != "" && Option(token).isDefined =>
        validate(validateUserApiKey(token))

      case Some(Authorization(GenericHttpCredentials(UserApiKeyScheme, _, params))) if params.keySet == Set("") =>
        validate(validateUserApiKey(params("")))

      case _ =>
        complete(AuthFailureError)
    }
  }

  private def validate(fn: Future[Option[AuthorizationProfile]]): Directive1[AuthorizationProfile] = {
    onSuccess(fn).flatMap {
      case Some(profile) =>
        provide(profile)
      case None =>
        complete(AuthFailureError)
    }
  }

  private[this] def validateSessionToken(token: String): Future[Option[AuthorizationProfile]] = {
    (authActor ? ValidateSessionTokenRequest(token)).mapTo[Option[AuthorizationProfile]]
  }

  private[this] def validateUserBearerToken(token: String): Future[Option[AuthorizationProfile]] = {
    (authActor ? ValidateUserBearerTokenRequest(token)).mapTo[Option[AuthorizationProfile]]
  }

  private[this] def validateUserApiKey(apiKey: String): Future[Option[AuthorizationProfile]] = {
    (authActor ? ValidateUserApiKeyRequest(apiKey)).mapTo[Option[AuthorizationProfile]]
  }
}
