package com.convergencelabs.server.frontend.rest

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import com.convergencelabs.server.datastore.AuthStoreActor.ValidateFailure
import com.convergencelabs.server.datastore.AuthStoreActor.ValidateRequest
import com.convergencelabs.server.datastore.AuthStoreActor.ValidateResponse
import com.convergencelabs.server.datastore.AuthStoreActor.ValidateSuccess

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
import akka.pattern.ask
import akka.util.Timeout

case class AuthenticationFailed(ok: Boolean, error: String) extends ResponseMessage

/**
 * This class provides a helper directive to authenticate users and validate
 * tokens
 */
class Authenticator(
  private[this] val authActor: ActorRef,
  private[this] val timeout: Timeout,
  private[this] val executionContext: ExecutionContext)
    extends JsonSupport {

  private[this] implicit val ec = executionContext
  private[this] implicit val t = timeout

  def requireAuthenticated(request: HttpRequest): Directive1[String] = {
    request.header[Authorization] match {
      case Some(Authorization(GenericHttpCredentials("token", _, params))) if params.keySet == Set("") =>
        onSuccess(validateToken(params(""))).flatMap {
          case Some(user) => provide(user)
          case None => complete(AuthFailureError)
        }
      case _ => complete(AuthFailureError)
    }
  }

  private[this] def validateToken(token: String): Future[Option[String]] = {
    (authActor ? ValidateRequest(token)).mapTo[ValidateResponse] map {
      case ValidateSuccess(uid) => Some(uid)
      case ValidateFailure => None
    }
  }
}
