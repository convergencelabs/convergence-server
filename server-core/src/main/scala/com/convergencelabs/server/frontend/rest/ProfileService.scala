package com.convergencelabs.server.frontend.rest

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import com.convergencelabs.server.datastore.ConvergenceUserManagerActor.GetConvergenceUser
import com.convergencelabs.server.datastore.User

import akka.actor.ActorRef
import akka.http.scaladsl.marshalling.ToResponseMarshallable.apply
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directive.addByNameNullaryApply
import akka.http.scaladsl.server.Directives._segmentStringToPathMatcher
import akka.http.scaladsl.server.Directives.complete
import akka.http.scaladsl.server.Directives.get
import akka.http.scaladsl.server.Directives.pathEnd
import akka.http.scaladsl.server.Directives.pathPrefix
import akka.pattern.ask
import akka.util.Timeout

case class CovergenceUserProfile(username: String, email: String, firstName: String, lastName: String, displayName: String)
case class UserProfileResponse(profile: CovergenceUserProfile) extends AbstractSuccessResponse

class ProfileService(
  private[this] val executionContext: ExecutionContext,
  private[this] val convergenceUserActor: ActorRef,
  private[this] val defaultTimeout: Timeout)
    extends JsonSupport {

  implicit val ec = executionContext
  implicit val t = defaultTimeout

  val route = { username: String =>
    pathPrefix("profile") {
      pathEnd {
        get {
          complete(getProfile(username))
        }
      }
    }
  }

  def getProfile(username: String): Future[RestResponse] = {
    (convergenceUserActor ? GetConvergenceUser(username)).mapTo[Option[User]].map {
      case Some(User(username, email, firstName, lastName, displayName)) =>
        (StatusCodes.OK, UserProfileResponse(CovergenceUserProfile(username, email, firstName, lastName, displayName)))
      case None =>
        NotFoundError
    }
  }
}
