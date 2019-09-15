package com.convergencelabs.server.api.rest

import java.time.Instant

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import com.convergencelabs.server.datastore.convergence.ConvergenceUserManagerActor.ConvergenceUserInfo
import com.convergencelabs.server.datastore.convergence.ConvergenceUserManagerActor.CreateConvergenceUserRequest
import com.convergencelabs.server.datastore.convergence.ConvergenceUserManagerActor.DeleteConvergenceUserRequest
import com.convergencelabs.server.datastore.convergence.ConvergenceUserManagerActor.GetConvergenceUser
import com.convergencelabs.server.datastore.convergence.ConvergenceUserManagerActor.GetConvergenceUsers
import com.convergencelabs.server.datastore.convergence.ConvergenceUserManagerActor.SetPasswordRequest
import com.convergencelabs.server.datastore.convergence.ConvergenceUserManagerActor.UpdateConvergenceUserRequest
import com.convergencelabs.server.datastore.convergence.UserStore.User
import com.convergencelabs.server.security.AuthorizationProfile

import akka.actor.ActorRef
import akka.http.scaladsl.marshalling.ToResponseMarshallable.apply
import akka.http.scaladsl.server.Directive.addByNameNullaryApply
import akka.http.scaladsl.server.Directive.addDirectiveApply
import akka.http.scaladsl.server.Directives._enhanceRouteWithConcatenation
import akka.http.scaladsl.server.Directives._segmentStringToPathMatcher
import akka.http.scaladsl.server.Directives._string2NR
import akka.http.scaladsl.server.Directives.as
import akka.http.scaladsl.server.Directives.complete
import akka.http.scaladsl.server.Directives.delete
import akka.http.scaladsl.server.Directives.entity
import akka.http.scaladsl.server.Directives.get
import akka.http.scaladsl.server.Directives.parameters
import akka.http.scaladsl.server.Directives.path
import akka.http.scaladsl.server.Directives.pathEnd
import akka.http.scaladsl.server.Directives.pathPrefix
import akka.http.scaladsl.server.Directives.post
import akka.http.scaladsl.server.Directives.put
import akka.util.Timeout

object ConvergenceUserService {
  case class CreateUserRequest(username: String, firstName: Option[String], lastName: Option[String], displayName: String, email: String, serverRole: String, password: String)
  case class UserPublicData(username: String, displayName: String)
  case class UserData(username: String, firstName: String, lastName: String, displayName: String, email: String, lastLogin: Option[Instant], serverRole: String)
  case class PasswordData(password: String)
  case class UpdateUserData(firstName: String, lastName: String, displayName: String, email: String, serverRole: String)
}

class ConvergenceUserService(
  private[this] val executionContext: ExecutionContext,
  private[this] val userManagerActor: ActorRef,
  private[this] val defaultTimeout: Timeout) extends JsonSupport {

  import ConvergenceUserService._
  import akka.http.scaladsl.server.Directives.Segment
  import akka.pattern.ask

  implicit val ec = executionContext
  implicit val t = defaultTimeout

  val route = { authProfile: AuthorizationProfile =>
    pathPrefix("users") {
      pathEnd {
        get {
          parameters("filter".?, "limit".as[Int].?, "offset".as[Int].?) { (filter, limit, offset) =>
            complete(getUsers(filter, limit, offset))
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
          } ~ put {
            entity(as[UpdateUserData]) { request =>
              complete(updateUser(username, request))
            }
          }
        } ~ path("password") {
          post {
            entity(as[PasswordData]) { request =>
              complete(setUserPassword(username, request))
            }
          }
        }
      }
    }
  }

  def getUsers(filter: Option[String], limit: Option[Int], offset: Option[Int]): Future[RestResponse] = {
    (userManagerActor ? GetConvergenceUsers(filter, limit, offset)).mapTo[Set[ConvergenceUserInfo]] map { users =>
      val publicData = users.map {
        case ConvergenceUserInfo(User(username, email, firstName, lastName, displayName, lastLogin), globalRole) =>
          UserData(username, firstName, lastName, displayName, email, lastLogin, globalRole)
      }
      okResponse(publicData)
    }
  }

  def getUser(username: String): Future[RestResponse] = {
    (userManagerActor ? GetConvergenceUser(username)).mapTo[Option[ConvergenceUserInfo]] map { user =>
      val publicUser = user.map {
        case ConvergenceUserInfo(User(username, email, firstName, lastName, displayName, lastLogin), globalRole) =>
          UserData(username, firstName, lastName, displayName, email, lastLogin, globalRole)
      }
      okResponse(publicUser)
    }
  }

  def createConvergenceUser(createRequest: CreateUserRequest): Future[RestResponse] = {
    val CreateUserRequest(username, firstName, lastName, displayName, email, serverRole, password) = createRequest
    val message = CreateConvergenceUserRequest(username, email, firstName.getOrElse(""), lastName.getOrElse(""), displayName, password, serverRole)
    (userManagerActor ? message) map { _ => CreatedResponse }
  }

  def deleteConvergenceUserRequest(username: String): Future[RestResponse] = {
    val message = DeleteConvergenceUserRequest(username)
    (userManagerActor ? message) map { _ => CreatedResponse }
  }

  def updateUser(username: String, updateData: UpdateUserData): Future[RestResponse] = {
    val UpdateUserData(firstName, lastName, displayName, email, serverRole) = updateData
    val message = UpdateConvergenceUserRequest(username, email, firstName, lastName, displayName, serverRole)
    (userManagerActor ? message).mapTo[Unit] map
      (_ => OkResponse)
  }

  def setUserPassword(username: String, passwordData: PasswordData): Future[RestResponse] = {
    val PasswordData(password) = passwordData
    (userManagerActor ? SetPasswordRequest(username, password)).mapTo[Unit] map
      (_ => OkResponse)
  }
}
