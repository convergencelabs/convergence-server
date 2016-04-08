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
import com.convergencelabs.server.datastore.AuthStoreActor.ValidateRequest
import com.convergencelabs.server.datastore.AuthStoreActor.ValidateResponse
import com.convergencelabs.server.datastore.AuthStoreActor.ValidateSuccess
import com.convergencelabs.server.datastore.AuthStoreActor.ValidateFailure
import scala.util.Try
import scala.util.Success
import scala.util.Failure

case class AuthenticationFailed(ok: Boolean, error: String) extends ResponseMessage


class Authenticator(authActor: ActorRef, timeout: Timeout, executionContext: ExecutionContext) extends JsonSupport {

  val authFailed = ErrorResponse("Unauthroized")
  
  import BasicDirectives._
  import RouteDirectives._
  import FutureDirectives._
  import ParameterDirectives._
  
  implicit val ec = executionContext
  implicit val t = timeout

  def validateToken(token: String): Future[Option[String]] = {
     (authActor ? ValidateRequest(token)).mapTo[Try[ValidateResponse]] map {
       case Success(ValidateSuccess(uid)) => Some(uid)
       case Success(ValidateFailure) => None
       case Failure(cause) => None
     }
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
