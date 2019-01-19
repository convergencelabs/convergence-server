package com.convergencelabs.server.frontend.rest

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import com.convergencelabs.server.datastore.convergence.UserStore.User
import com.convergencelabs.server.datastore.convergence.ConvergenceUserManagerActor.CreateConvergenceUserRequest
import com.convergencelabs.server.datastore.convergence.ConvergenceUserManagerActor.DeleteConvergenceUserRequest
import com.convergencelabs.server.datastore.convergence.ConvergenceUserManagerActor.GetConvergenceUser
import com.convergencelabs.server.datastore.convergence.ConvergenceUserManagerActor.GetConvergenceUsers
import com.convergencelabs.server.datastore.DuplicateValueException
import com.convergencelabs.server.datastore.InvalidValueExcpetion

import akka.actor.ActorRef
import akka.http.scaladsl.marshalling.ToResponseMarshallable.apply
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directive.addByNameNullaryApply
import akka.http.scaladsl.server.Directive.addDirectiveApply
import akka.http.scaladsl.server.Directives.Segment
import akka.http.scaladsl.server.Directives._enhanceRouteWithConcatenation
import akka.http.scaladsl.server.Directives._segmentStringToPathMatcher
import akka.http.scaladsl.server.Directives._string2NR
import akka.http.scaladsl.server.Directives.complete
import akka.http.scaladsl.server.Directives.delete
import akka.http.scaladsl.server.Directives.parameters
import akka.http.scaladsl.server.Directives.get
import akka.http.scaladsl.server.Directives.handleWith
import akka.http.scaladsl.server.Directives.handleExceptions
import akka.http.scaladsl.server.Directives.pathEnd
import akka.http.scaladsl.server.Directives.pathPrefix
import akka.http.scaladsl.server.Directives.post
import akka.pattern.ask
import akka.util.Timeout
import akka.http.scaladsl.server.ExceptionHandler

object ConvergenceUserService {
  case class GetUsersResponse(users: List[UserPublicData])
  case class GetUserResponse(user: Option[UserPublicData])
  
  case class UserPublicData(username: String, displayName: String)
}

class ConvergenceUserService(
    private[this] val executionContext: ExecutionContext,
    private[this] val userManagerActor: ActorRef,
    private[this] val defaultTimeout: Timeout) extends JsonSupport {

  import ConvergenceUserService._
  
  implicit val ec = executionContext
  implicit val t = defaultTimeout

  val route = { adminUser: String =>
    pathPrefix("users") {
      pathEnd {
        get {
          parameters("filter".?, "limit".as[Int].?, "offset".as[Int].?) { (filter, limit, offset) =>
            complete(getUsersRequest(filter, limit, offset))
          }
        }
      } ~ pathPrefix(Segment) { username =>
        pathEnd {
          get {
            complete(getUser(username))
          }
        }
      }
    }
  }

  def getUsersRequest(filter: Option[String], limit: Option[Int], offset: Option[Int]): Future[RestResponse] = {
    (userManagerActor ? GetConvergenceUsers(filter, limit, offset)).mapTo[List[User]] map { users => 
      val publicData = users.map { case User(username, email, firstName, lastName, displayName) => UserPublicData(username, displayName)}
      okResponse(GetUsersResponse(publicData))
    }
  }

  def getUser(username: String): Future[RestResponse] = {
    (userManagerActor ? GetConvergenceUser(username)).mapTo[Option[User]] map { user =>
       val publicUser = user.map { case User(username, email, firstName, lastName, displayName) => UserPublicData(username, displayName) }
      okResponse(GetUserResponse(publicUser))
    }
  }
}
