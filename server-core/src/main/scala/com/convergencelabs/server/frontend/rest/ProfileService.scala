package com.convergencelabs.server.frontend.rest

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import com.convergencelabs.server.datastore.convergence.ConvergenceUserManagerActor.GetConvergenceUser
import com.convergencelabs.server.datastore.convergence.ConvergenceUserManagerActor.UpdateConvergenceUserRequest
import com.convergencelabs.server.datastore.convergence.UserStore.User

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
import akka.pattern.ask
import akka.util.Timeout

case class CovergenceUserProfile(username: String, email: String, firstName: String, lastName: String, displayName: String)
case class UserProfileResponse(profile: CovergenceUserProfile) extends AbstractSuccessResponse
case class UpdateProfileRequest(email: String, firstName: String, lastName: String, displayName: String)

class ProfileService(
  private[this] val executionContext: ExecutionContext,
  private[this] val convergenceUserActor: ActorRef,
  private[this] val defaultTimeout: Timeout)
    extends JsonSupport {

  implicit val ec = executionContext
  implicit val t = defaultTimeout

  val route = { username: String =>
    path("profile") {
      get {
        complete(getProfile(username))
      } ~
      put {
        entity(as[UpdateProfileRequest]) { profile =>
          complete(updateProfile(username, profile))
        }
      }
    }
  }

  def getProfile(username: String): Future[RestResponse] = {
    val message = GetConvergenceUser(username)
    (convergenceUserActor ? message).mapTo[Option[User]].map {
      case Some(User(username, email, firstName, lastName, displayName)) =>
        (StatusCodes.OK, UserProfileResponse(CovergenceUserProfile(username, email, firstName, lastName, displayName)))
      case None =>
        NotFoundError
    }
  }
  
  def updateProfile(username: String, profile: UpdateProfileRequest): Future[RestResponse] = {
    val UpdateProfileRequest(email, firstName, lastName, displayName) = profile
    val message = UpdateConvergenceUserRequest(username, email, firstName, lastName, displayName)
    (convergenceUserActor ? message) map { _ => OkResponse }
  }
}
