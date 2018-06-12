package com.convergencelabs.server.frontend.rest

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import com.convergencelabs.server.datastore.convergnece.AuthStoreActor.ValidateSessionTokenRequest
import com.convergencelabs.server.datastore.convergnece.AuthStoreActor.ValidateUserApiKeyRequest

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

case class AuthenticationFailed(ok: Boolean, error: String) extends ResponseMessage

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

  def requireAuthenticatedUser(request: HttpRequest): Directive1[String] = {
    request.header[Authorization] match {
      case Some(Authorization(GenericHttpCredentials("SessionToken", _, params))) if params.keySet == Set("") =>
        onSuccess(validateSessionToken(params(""))).flatMap {
          case Some(username) =>
            provide(username)
          case None =>
            complete(AuthFailureError)
        }

      case Some(Authorization(GenericHttpCredentials("UserApiKey", _, params))) if params.keySet == Set("") =>
        onSuccess(validateUserApiKey(params(""))).flatMap {
          case Some(username) =>
            provide(username)
          case None =>
            complete(AuthFailureError)
        }
      case _ =>
        complete(AuthFailureError)
    }
  }

  private[this] def validateSessionToken(token: String): Future[Option[String]] = {
    (authActor ? ValidateSessionTokenRequest(token)).mapTo[Option[String]]
  }

  private[this] def validateUserApiKey(token: String): Future[Option[String]] = {
    (authActor ? ValidateUserApiKeyRequest(token)).mapTo[Option[String]]
  }

  def requireAuthenticatedAdmin(request: HttpRequest): Directive1[String] = {
    request.header[Authorization] match {
      case Some(Authorization(GenericHttpCredentials("MasterAdminApiKey", _, params))) if params.keySet == Set("") =>
        (masterAdminToken == params("")) match {
          case true =>
            provide("admin")
          case false =>
            complete(AuthFailureError)
        }
      case _ =>
        complete(AuthFailureError)
    }
  }
}
