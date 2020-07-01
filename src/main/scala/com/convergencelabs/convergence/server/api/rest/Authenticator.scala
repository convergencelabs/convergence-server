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
import com.convergencelabs.convergence.server.backend.services.server.AuthenticationActor
import com.convergencelabs.convergence.server.backend.services.server.AuthenticationActor._
import com.convergencelabs.convergence.server.security.AuthorizationProfile

import scala.concurrent.{ExecutionContext, Future}

/**
 * This class provides a helper directive to authenticate users and validate
 * tokens / API keys. It provides a Akka HTTP Route Directive that an protect
 * routes by validating the Authorization header in HTTP Requests.
 */
private[rest] class Authenticator(authActor: ActorRef[AuthenticationActor.Message],
                                  private[this] implicit val scheduler: Scheduler,
                                  private[this] implicit val ec: ExecutionContext,
                                  private[this] implicit val timeout: Timeout)
  extends JsonSupport {

  import Authenticator._

  /**
   * A directive which take the current HTTP Request (which can be provided via
   * the built in extractRequest directive) and validates the user credentials
   * using one of several authentication schemes.  This directive will examine
   * the HTTP Authorization header (see
   *  https://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html.). It will look
   *  for one of the following schemes: "SessionToken", "BearerToken",
   *  "UserApiKey"; and validate them appropriately using the
   *  AuthenticationActor supplied to the constructor of this class.
   *
   *  If the authentication succeeds, the user's AuthenticationProfile will
   *  be provided to the child route. If the authentication fails the
   *  route will be completed with either an 401 (unauthorized) or
   *  a 500 (internal server error) in the case that there was an
   *  error in the authentication process.
   *
   * @param request The HttpRequest to interrogate.
   * @return
   */
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

  private[this] def validateSessionToken(token: String): Future[ValidateResponse] = {
    authActor.ask[ValidateResponse](ref => ValidateSessionTokenRequest(token, ref))
  }

  private[this] def validateUserBearerToken(token: String): Future[ValidateResponse] = {
    authActor.ask[ValidateResponse](ref => ValidateUserBearerTokenRequest(token, ref))
  }

  private[this] def validateUserApiKey(apiKey: String): Future[ValidateResponse] = {
    authActor.ask[ValidateResponse](ref => ValidateUserApiKeyRequest(apiKey, ref))
  }

  private def validate(f: Future[ValidateResponse]): Directive1[AuthorizationProfile] = {
    onSuccess(f).flatMap(_.profile.fold({
      case AuthenticationActor.ValidationFailed() =>
        complete(AuthFailureError)
      case AuthenticationActor.UnknownError() =>
        complete(InternalServerError)
    }, { profile =>
      provide(AuthorizationProfile(profile))
    }))
  }
}

object Authenticator {
  /**
   * The HTTP Authorization scheme for validating HttpAPI Sessions.
   */
  val SessionTokenScheme = "SessionToken"

  /**
   * The HTTP Authorization scheme for validating uer Bearer tokens.
   */
  val BearerTokenScheme = "BearerToken"

  /**
   * The Http Authorization scheme for validating user API Keys.
   */
  val UserApiKeyScheme = "UserApiKey"
}
