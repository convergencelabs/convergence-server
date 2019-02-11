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
import akka.http.scaladsl.server.Directives.path
import akka.http.scaladsl.server.Directives.post
import akka.http.scaladsl.server.Directives.entity
import akka.http.scaladsl.server.Directives.as

import akka.util.Timeout
import akka.http.scaladsl.server.ExceptionHandler
import com.convergencelabs.server.security.AuthorizationProfile
import java.time.Instant
import com.convergencelabs.server.datastore.convergence.ConvergenceUserManagerActor.GetConvergenceUserInfo
import com.convergencelabs.server.datastore.convergence.ConvergenceUserManagerActor.SetPasswordRequest
import com.convergencelabs.server.datastore.convergence.ConvergenceUserManagerActor.ConvergenceUserOverview

object ConvergenceUserService {
  case class GetUsersResponse(users: List[UserPublicData])
  case class GetUserResponse(user: Option[UserPublicData])
  case class CreateUserRequest(user: UserData, password: String, serverRole: String)
  case class UserPublicData(username: String, displayName: String)
  case class UserData(username: String, firstName: String, lastName: String, displayName: String, email: String)
  case class UserInfoData(user: UserData, lastLogin: Option[Instant], serverRole: String)
  case class PasswordData(password: String)
}

class ConvergenceUserService(
  private[this] val executionContext: ExecutionContext,
  private[this] val userManagerActor: ActorRef,
  private[this] val defaultTimeout: Timeout) extends JsonSupport {

  import ConvergenceUserService._
  import akka.pattern.ask

  implicit val ec = executionContext
  implicit val t = defaultTimeout

  val route = { authProfile: AuthorizationProfile =>
    pathPrefix("users") {
      pathEnd {
        get {
          parameters("filter".?, "limit".as[Int].?, "offset".as[Int].?) { (filter, limit, offset) =>
            complete(getUsersRequest(filter, limit, offset))
          }
        } ~ post {
          entity(as[CreateUserRequest]) { request =>
            complete(createConvergenceUser(request))
          }
        }
      } ~ pathPrefix(Segment) { username =>
        pathEnd {
          get {
            complete(getUser(username))
          } ~ delete {
            complete(deleteConvergenceUserRequest(username))
          }
        } ~ path("password") {
          post {
            entity(as[PasswordData]) { request =>
              complete(setUserPassword(username, request))
            }
          }
        }
      }
    } ~ path("userInfo") {
      get {
        parameters("filter".?, "limit".as[Int].?, "offset".as[Int].?) { (filter, limit, offset) =>
          complete(getUserInfo(filter, limit, offset))
        }
      }
    }
  }

  def getUsersRequest(filter: Option[String], limit: Option[Int], offset: Option[Int]): Future[RestResponse] = {
    (userManagerActor ? GetConvergenceUsers(filter, limit, offset)).mapTo[List[User]] map { users =>
      val publicData = users.map { case User(username, email, firstName, lastName, displayName, lastLogin) => UserPublicData(username, displayName) }
      okResponse(publicData)
    }
  }

  def getUser(username: String): Future[RestResponse] = {
    (userManagerActor ? GetConvergenceUser(username)).mapTo[Option[User]] map { user =>
      val publicUser = user.map { case User(username, email, firstName, lastName, displayName, lastLogin) => UserPublicData(username, displayName) }
      okResponse(GetUserResponse(publicUser))
    }
  }

  def getUserInfo(filter: Option[String], limit: Option[Int], offset: Option[Int]): Future[RestResponse] = {
    (userManagerActor ? GetConvergenceUserInfo(filter, limit, offset)).mapTo[Set[ConvergenceUserOverview]] map { users =>
      val publicData = users.map {
        case ConvergenceUserOverview(User(username, email, firstName, lastName, displayName, lastLogin), globalRole) =>
          UserInfoData(UserData(username, firstName, lastName, displayName, email), lastLogin, globalRole)
      }
      okResponse(publicData)
    }
  }

  def createConvergenceUser(createRequest: CreateUserRequest): Future[RestResponse] = {
    val CreateUserRequest(UserData(username, firstName, lastName, displayName, email), password, globalRole) = createRequest
    val message = CreateConvergenceUserRequest(username, email, firstName, lastName, displayName, password, globalRole)
    (userManagerActor ? message) map { _ => CreatedResponse }
  }

  def deleteConvergenceUserRequest(username: String): Future[RestResponse] = {
    val message = DeleteConvergenceUserRequest(username)
    (userManagerActor ? message) map { _ => CreatedResponse }
  }

  def setUserPassword(username: String, passwordData: PasswordData): Future[RestResponse] = {
    val PasswordData(password) = passwordData
    (userManagerActor ? SetPasswordRequest(username, password)).mapTo[Unit] map
      (_ => OkResponse)
  }
}
