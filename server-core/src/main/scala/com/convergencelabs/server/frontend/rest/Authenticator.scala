package com.convergencelabs.server.frontend.rest

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import com.convergencelabs.server.datastore.convergence.AuthenticationActor.ValidateSessionTokenRequest
import com.convergencelabs.server.datastore.convergence.AuthenticationActor.ValidateUserBearerTokenRequest

import akka.actor.ActorRef
import akka.http.scaladsl.marshalling.ToResponseMarshallable.apply
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.headers.Authorization
import akka.http.scaladsl.model.headers.GenericHttpCredentials
import akka.http.scaladsl.server.Directive.SingleValueModifiers
import akka.http.scaladsl.server.Directive1
import akka.http.scaladsl.server.StandardRoute.toDirective
import akka.http.scaladsl.server.directives.BasicDirectives.provide
import akka.http.scaladsl.server.directives.FutureDirectives.onSuccess
import akka.http.scaladsl.server.directives.OnSuccessMagnet.apply
import akka.http.scaladsl.server.directives.RouteDirectives.complete
import akka.util.Timeout
import com.convergencelabs.server.security.AuthorizationProfile

/**
 * This class provides a helper directive to authenticate users and validate
 * tokens
 */
class Authenticator(
  private[this] val authActor: ActorRef,
  private[this] val masterAdminToken: String,
  private[this] val timeout: Timeout,
  private[this] val executionContext: ExecutionContext)
  extends JsonSupport {

  import akka.pattern.ask

  private[this] implicit val ec = executionContext
  private[this] implicit val t = timeout

  private[this] val SessionTokenScheme = "SessionToken"
  private[this] val BearerTokenScheme = "BearerToken"
  private[this] val UserApiKeyScheme = "UserApiKey"

  def requireAuthenticatedUser(request: HttpRequest): Directive1[AuthorizationProfile] = {
    request.header[Authorization] match {
      case Some(Authorization(GenericHttpCredentials(SessionTokenScheme, token, _))) if token != "" && Option(token).isDefined =>
        onSuccess(validateSessionToken(token)).flatMap {
          case Some(profile) =>
            provide(profile)
          case None =>
            complete(AuthFailureError)
        }

      case Some(Authorization(GenericHttpCredentials(SessionTokenScheme, _, params))) if params.keySet == Set("") =>
        onSuccess(validateSessionToken(params(""))).flatMap {
          case Some(profile) =>
            provide(profile)
          case None =>
            complete(AuthFailureError)
        }

      case Some(Authorization(GenericHttpCredentials(BearerTokenScheme, token, _))) if token != "" && Option(token).isDefined =>
        onSuccess(validateUserBearerTokeny(token)).flatMap {
          case Some(profile) =>
            provide(profile)
          case None =>
            complete(AuthFailureError)
        }

      case Some(Authorization(GenericHttpCredentials(BearerTokenScheme, _, params))) if params.keySet == Set("") =>
        onSuccess(validateUserBearerTokeny(params(""))).flatMap {
          case Some(profile) =>
            provide(profile)
          case None =>
            complete(AuthFailureError)
        }
      case _ =>
        complete(AuthFailureError)
    }
  }

  private[this] def validateSessionToken(token: String): Future[Option[AuthorizationProfile]] = {
    (authActor ? ValidateSessionTokenRequest(token)).mapTo[Option[AuthorizationProfile]]
  }

  private[this] def validateUserBearerTokeny(token: String): Future[Option[AuthorizationProfile]] = {
    (authActor ? ValidateUserBearerTokenRequest(token)).mapTo[Option[AuthorizationProfile]]
  }
}
