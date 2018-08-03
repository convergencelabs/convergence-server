package com.convergencelabs.server.frontend.rest

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import com.convergencelabs.server.datastore.convergence.ConvergenceUserManagerActor.GetUserApiKeyRequest
import com.convergencelabs.server.datastore.convergence.ConvergenceUserManagerActor.RegenerateUserApiKeyRequest
import com.convergencelabs.server.datastore.convergence.ConvergenceUserManagerActor.ClearUserApiKeyRequest

import akka.actor.ActorRef
import akka.http.scaladsl.marshalling.ToResponseMarshallable.apply
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directive.addByNameNullaryApply
import akka.http.scaladsl.server.Directive.addDirectiveApply
import akka.http.scaladsl.server.Directives._enhanceRouteWithConcatenation
import akka.http.scaladsl.server.Directives._segmentStringToPathMatcher
import akka.http.scaladsl.server.Directives.as
import akka.http.scaladsl.server.Directives.complete
import akka.http.scaladsl.server.Directives.entity
import akka.http.scaladsl.server.Directives.get
import akka.http.scaladsl.server.Directives.path
import akka.http.scaladsl.server.Directives.put
import akka.http.scaladsl.server.Directives.delete
import akka.pattern.ask
import akka.util.Timeout

object UserApiKeyService {
  case class UserApiKeyResponse(apiKey: Option[String]) extends AbstractSuccessResponse
}

class UserApiKeyService(
  private[this] val executionContext: ExecutionContext,
  private[this] val convergenceUserActor: ActorRef,
  private[this] val defaultTimeout: Timeout)
    extends JsonSupport {

  import UserApiKeyService._

  implicit val ec = executionContext
  implicit val t = defaultTimeout

  val route = { username: String =>
    path("apiKey") {
      get {
        complete(getApiKey(username))
      } ~
        put {
          complete(regenerateApiKey(username))
        } ~
        delete {
          complete(clearApiKey(username))
        }
    }
  }

  def getApiKey(username: String): Future[RestResponse] = {
    val message = GetUserApiKeyRequest(username)
    (convergenceUserActor ? message).mapTo[Option[String]].map { apiKey => (StatusCodes.OK, UserApiKeyResponse(apiKey)) }
  }

  def regenerateApiKey(username: String): Future[RestResponse] = {
    val message = RegenerateUserApiKeyRequest(username)
    (convergenceUserActor ? message).mapTo[String].map { apiKey => (StatusCodes.OK, UserApiKeyResponse(Some(apiKey))) }
  }

  def clearApiKey(username: String): Future[RestResponse] = {
    val message = ClearUserApiKeyRequest(username)
    (convergenceUserActor ? message).map (_ => OkResponse)
  }
}
