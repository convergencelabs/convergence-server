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
import com.convergencelabs.server.datastore.AuthStoreActor.TokenExpirationRequest
import com.convergencelabs.server.datastore.AuthStoreActor.TokenExpirationResponse
import com.convergencelabs.server.datastore.AuthStoreActor.TokenExpirationSuccess
import com.convergencelabs.server.datastore.AuthStoreActor.TokenExpirationFailure

case class TokenResponse(token: String, expiration: Long) extends AbstractSuccessResponse
case class ExpirationResponse(valid: Boolean, username: Option[String], delta: Option[Long]) extends AbstractSuccessResponse

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
    } ~ pathPrefix("check") {
      pathEnd {
        post {
          handleWith(tokenExprirationCheck)
        }
      }
    }
  }

  def authRequest(req: AuthRequest): Future[RestResponse] = {
    (authActor ? req).mapTo[AuthResponse].map {
      case AuthSuccess(token, expiration) => (StatusCodes.OK, TokenResponse(token, expiration.toMillis()))
      case AuthFailure => AuthFailureError
    }
  }

  def tokenExprirationCheck(req: TokenExpirationRequest): Future[RestResponse] = {
    (authActor ? req).mapTo[TokenExpirationResponse].map {
      case TokenExpirationSuccess(username, exprieDelta) =>
        (StatusCodes.OK, ExpirationResponse(true, Some(username), Some(exprieDelta.toMillis())))
      case TokenExpirationFailure => 
        (StatusCodes.OK, ExpirationResponse(false, None, None))
    }
  }
}
