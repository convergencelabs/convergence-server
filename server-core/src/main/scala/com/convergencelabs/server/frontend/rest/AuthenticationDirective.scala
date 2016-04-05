package com.convergencelabs.server.frontend.rest

import akka.http.scaladsl.server.directives.{ RouteDirectives, BasicDirectives, ParameterDirectives, FutureDirectives }
import akka.http.scaladsl.server.Directive1
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.model.StatusCodes
import scala.concurrent.Future
import akka.http.scaladsl.server.StandardRoute
import akka.actor.ActorRef
import akka.util.Timeout
import scala.concurrent.ExecutionContext
import akka.pattern.ask

case class AuthenticationFailed(ok: Boolean, error: String) extends ResponseMessage


class Authenticator(userActor: ActorRef, timeout: Timeout, executionContext: ExecutionContext) extends JsonService {

  val authFailed = ErrorMessage("Unauthroized")
  
  import BasicDirectives._
  import RouteDirectives._
  import FutureDirectives._
  import ParameterDirectives._
  
  implicit val ec = executionContext
  implicit val t = timeout

  def validateToken(authToken: String): Future[Option[String]] = {
    // need to change this to send the correct case class and map to the
    // correct response.
    // (userActor ? "some case class").mapTo[Option[String]]
    Future.successful(Some("some user"))
  }

  def rejectAuthentication(): StandardRoute = {
    complete(StatusCodes.Unauthorized, authFailed)
  }
  
  val requireAuthenticated: Directive1[String] = {
    parameter("token".?).flatMap {
      case Some(token) =>
        onSuccess(validateToken(token)).flatMap {
          case Some(user) => 
            provide(user)
          case None => 
            rejectAuthentication()
        }
      case None => 
        rejectAuthentication()
    }
  }
}
