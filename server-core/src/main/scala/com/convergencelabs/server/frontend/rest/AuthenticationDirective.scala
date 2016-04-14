package com.convergencelabs.server.frontend.rest

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import com.convergencelabs.server.datastore.AuthStoreActor.ValidateFailure
import com.convergencelabs.server.datastore.AuthStoreActor.ValidateRequest
import com.convergencelabs.server.datastore.AuthStoreActor.ValidateResponse
import com.convergencelabs.server.datastore.AuthStoreActor.ValidateSuccess

import akka.actor.ActorRef
import akka.http.scaladsl.marshalling.ToResponseMarshallable.apply
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directive.SingleValueModifiers
import akka.http.scaladsl.server.Directive1
import akka.http.scaladsl.server.StandardRoute
import akka.http.scaladsl.server.StandardRoute.toDirective
import akka.http.scaladsl.server.directives.BasicDirectives.provide
import akka.http.scaladsl.server.directives.FutureDirectives.onSuccess
import akka.http.scaladsl.server.directives.OnSuccessMagnet.apply
import akka.http.scaladsl.server.directives.ParameterDirectives.parameter
import akka.http.scaladsl.server.directives.ParameterDirectives.string2NR
import akka.http.scaladsl.server.directives.RouteDirectives.complete
import akka.pattern.ask
import akka.util.Timeout
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.headers.Authorization
import akka.http.scaladsl.model.headers.GenericHttpCredentials

case class AuthenticationFailed(ok: Boolean, error: String) extends ResponseMessage

class Authenticator(authActor: ActorRef, timeout: Timeout, executionContext: ExecutionContext) extends JsonSupport {

  implicit val ec = executionContext
  implicit val t = timeout

  def validateToken(token: String): Future[Option[String]] = {
    (authActor ? ValidateRequest(token)).mapTo[ValidateResponse] map {
      case ValidateSuccess(uid) => Some(uid)
      case ValidateFailure      => None
    }
  }

  def requireAuthenticated(request: HttpRequest): Directive1[String] = {
    val auth = for {
      Authorization(GenericHttpCredentials(scheme, _, params)) <- request.header[Authorization]
    } yield (scheme, params)

    auth match {
      case Some(("token", params)) if params.size == 1 && params.isDefinedAt("") => {
        params.get("") match {
          case Some(token) => onSuccess(validateToken(token)).flatMap {
            case Some(user) => provide(user)
            case None       => complete(AuthFailureError)
          }
          case None => complete(AuthFailureError)
        }
      }
      case None => complete(AuthFailureError)
    }
  }
}
