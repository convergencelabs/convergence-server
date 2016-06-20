package com.convergencelabs.server.frontend.rest

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import akka.http.scaladsl.model.StatusCodes
import akka.actor.ActorRef
import akka.util.Timeout
import akka.http.scaladsl.server.Directives._
import akka.pattern._

import com.convergencelabs.server.datastore.AuthStoreActor.AuthRequest
import com.convergencelabs.server.datastore.AuthStoreActor.AuthSuccess
import com.convergencelabs.server.datastore.AuthStoreActor.AuthFailure
import com.convergencelabs.server.datastore.AuthStoreActor.AuthResponse


case class TokenResponse(token: String) extends AbstractSuccessResponse

class AuthService(
  private[this] val executionContext: ExecutionContext,
  private[this] val authActor: ActorRef,
  private[this] val defaultTimeout: Timeout)
    extends JsonSupport {
 
  implicit val ec = executionContext
  implicit val t = defaultTimeout

  val route = pathPrefix("auth") {
    pathEnd {
      post {
        handleWith(authRequest)
      }
    }
  }

  def authRequest(req: AuthRequest): Future[RestResponse] = {
    (authActor ? req).mapTo[AuthResponse].map {
      case AuthSuccess(uid, token) => (StatusCodes.OK, TokenResponse(token))
      case AuthFailure             => AuthFailureError
    }
  }
}
