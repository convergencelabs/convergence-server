package com.convergencelabs.server.frontend.rest

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import akka.actor.ActorRef
import akka.http.scaladsl.server.Directives.handleWith
import akka.http.scaladsl.server.Directives.pathEnd
import akka.http.scaladsl.server.Directives.pathPrefix
import akka.http.scaladsl.server.Directives.post
import akka.http.scaladsl.server.Directives.get
import akka.http.scaladsl.server.Directives.complete
import akka.http.scaladsl.server.Directives.segmentStringToPathMatcher
import akka.pattern.ask
import akka.util.Timeout
import com.convergencelabs.server.frontend.rest.ConvergenceAdminService.CreateUserRequest
import com.convergencelabs.server.datastore.ConvergenceUserManagerActor.CreateConvergenceUserRequest
import com.convergencelabs.server.datastore.CreateResult
import com.convergencelabs.server.datastore.CreateSuccess
import com.convergencelabs.server.datastore.DuplicateValue
import com.convergencelabs.server.datastore.InvalidValue

object ConvergenceAdminService {
  case class CreateUserRequest(username: String, firstName: String, lastName: String, email: String, password: String)
}

class ConvergenceAdminService(
  private[this] val executionContext: ExecutionContext,
  private[this] val userManagerActor: ActorRef,
  private[this] val defaultTimeout: Timeout) extends JsonSupport {

  implicit val ec = executionContext
  implicit val t = defaultTimeout
  
  val route = { adminUser: String =>
    pathPrefix("users") {
      pathEnd {
        post {
          handleWith(createConvergenceUserRequest)
        }
      }
    }
  }
  
 def createConvergenceUserRequest(createRequest: CreateUserRequest): Future[RestResponse] = {
    val CreateUserRequest(username, firstName, lastName, email, password) = createRequest
    (userManagerActor ? CreateConvergenceUserRequest(username, password)).mapTo[CreateResult[String]].map {
      case result: CreateSuccess[String] => CreateRestResponse
      case DuplicateValue                => DuplicateError
      case InvalidValue                  => InvalidValueError
    }
  }
}


