package com.convergencelabs.server.frontend.rest

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.model.HttpEntity
import akka.http.scaladsl.model.ContentTypes
import de.heikoseeberger.akkahttpjson4s.Json4sSupport
import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.Future
import scala.util.Success
import scala.concurrent.ExecutionContext
import scala.util.Failure
import akka.http.scaladsl.model.StatusCodes

case class AuthHttpResponse(ok: Boolean, token: Option[String]) extends ResponseMessage

class AuthService(
  private[this] val executionContext: ExecutionContext,
  private[this] val authActor: ActorRef,
  private[this] val defaultTimeout: Timeout)
    extends JsonService {

  implicit val ec = executionContext
  implicit val t = defaultTimeout

  val route = pathPrefix("auth") {
    pathEnd {
      post {
        handleWith(authRequest)
      }
    }
  }

  def authRequest(req: AuthRequest): Future[AuthHttpResponse] = {
    (authActor ? req).mapTo[AuthResponse].map {
      case AuthSuccess(token) => AuthHttpResponse(true, Some(token))
      case AuthFailure => AuthHttpResponse(false, None)
    }
  }
}
