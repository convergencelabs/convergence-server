package com.convergencelabs.server.frontend.rest

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import com.convergencelabs.server.datastore.convergence.ConvergenceUserManagerActor.GetConvergenceUser
import com.convergencelabs.server.datastore.convergence.ConvergenceUserManagerActor.GetUserBearerTokenRequest
import com.convergencelabs.server.datastore.convergence.ConvergenceUserManagerActor.RegenerateUserBearerTokenRequest
import com.convergencelabs.server.datastore.convergence.ConvergenceUserManagerActor.SetPasswordRequest
import com.convergencelabs.server.datastore.convergence.ConvergenceUserManagerActor.UpdateConvergenceUserRequest
import com.convergencelabs.server.datastore.convergence.UserStore.User

import akka.actor.ActorRef
import akka.http.scaladsl.marshalling.ToResponseMarshallable.apply
import akka.http.scaladsl.server.Directive.addByNameNullaryApply
import akka.http.scaladsl.server.Directive.addDirectiveApply
import akka.http.scaladsl.server.Directives._enhanceRouteWithConcatenation
import akka.http.scaladsl.server.Directives._segmentStringToPathMatcher
import akka.http.scaladsl.server.Directives.as
import akka.http.scaladsl.server.Directives.complete
import akka.http.scaladsl.server.Directives.entity
import akka.http.scaladsl.server.Directives.get
import akka.http.scaladsl.server.Directives.path
import akka.http.scaladsl.server.Directives.pathPrefix
import akka.http.scaladsl.server.Directives.put
import akka.util.Timeout
import grizzled.slf4j.Logging

object CurrentUserService {
  case class BearerTokenResponse(token: String)
  case class CovergenceUserProfile(username: String, email: String, firstName: String, lastName: String, displayName: String)
  case class UserProfileResponse(profile: CovergenceUserProfile)
  case class UpdateProfileRequest(email: String, firstName: String, lastName: String, displayName: String)
  case class PasswordSetRequest(password: String)
}

class CurrentUserService(
  private[this] val executionContext: ExecutionContext,
  private[this] val convergenceUserActor: ActorRef,
  private[this] val defaultTimeout: Timeout)
  extends JsonSupport
  with Logging {

  import CurrentUserService._
  import akka.pattern.ask

  implicit val ec = executionContext
  implicit val t = defaultTimeout

  val route = { username: String =>
    pathPrefix("user") {
      path("profile") {
        get {
          complete(getProfile(username))
        } ~
          put {
            entity(as[UpdateProfileRequest]) { profile =>
              complete(updateProfile(username, profile))
            }
          }
      } ~ path("bearerToken") {
        get {
          complete(getBearerToken(username))
        } ~ put {
          complete(regenerateBearerToken(username))
        }
      } ~ (path("password") & put) {
        entity(as[PasswordSetRequest]) { password =>
          complete(setPassword(username, password))
        }
      } ~ path("apiKeys") {
        get {
          complete(okResponse("apiKey"))
        }
      }
    }
  }

  def getBearerToken(username: String): Future[RestResponse] = {
    val message = GetUserBearerTokenRequest(username)
    (convergenceUserActor ? message).mapTo[Option[String]].map(okResponse(_))
  }

  def regenerateBearerToken(username: String): Future[RestResponse] = {
    val message = RegenerateUserBearerTokenRequest(username)
    (convergenceUserActor ? message).mapTo[String].map(okResponse(_))
  }

  def setPassword(username: String, request: PasswordSetRequest): Future[RestResponse] = {
    logger.debug(s"Received request to set the password for user: ${username}")
    val PasswordSetRequest(password) = request
    val message = SetPasswordRequest(username, password)
    (convergenceUserActor ? message) map { _ => OkResponse }
  }

  def getProfile(username: String): Future[RestResponse] = {
    val message = GetConvergenceUser(username)
    (convergenceUserActor ? message).mapTo[Option[User]].map {
      case Some(User(username, email, firstName, lastName, displayName)) =>
        okResponse(UserProfileResponse(CovergenceUserProfile(username, email, firstName, lastName, displayName)))
      case None =>
        notFoundResponse()
    }
  }

  def updateProfile(username: String, profile: UpdateProfileRequest): Future[RestResponse] = {
    val UpdateProfileRequest(email, firstName, lastName, displayName) = profile
    val message = UpdateConvergenceUserRequest(username, email, firstName, lastName, displayName)
    (convergenceUserActor ? message) map { _ => OkResponse }
  }
}
