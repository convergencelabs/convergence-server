package com.convergencelabs.server.frontend.rest

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import com.convergencelabs.server.datastore.AuthStoreActor.AuthFailure
import com.convergencelabs.server.datastore.AuthStoreActor.AuthRequest
import com.convergencelabs.server.datastore.AuthStoreActor.AuthResponse
import com.convergencelabs.server.datastore.AuthStoreActor.AuthSuccess

import akka.actor.ActorRef
import akka.http.scaladsl.server.Directives._
import akka.pattern._
import akka.util.Timeout
import com.convergencelabs.server.datastore.CreateResult
import com.convergencelabs.server.datastore.CreateSuccess
import com.convergencelabs.server.datastore.DuplicateValue
import com.convergencelabs.server.datastore.InvalidValue
import com.convergencelabs.server.datastore.RegistrationActor.RegisterUser
import com.convergencelabs.server.datastore.RegistrationActor.AddRegistration
import com.convergencelabs.server.datastore.RegistrationActor.ApproveRegistration
import com.convergencelabs.server.datastore.UpdateResult
import com.convergencelabs.server.datastore.UpdateSuccess
import com.convergencelabs.server.datastore.NotFound
import com.convergencelabs.server.datastore.RegistrationActor.RejectRegistration

case class Registration(username: String, fname: String, lname: String, email: String, password: String, token: String)
case class RegistrationRequest(fname: String, lname: String, email: String)
case class RegistrationApproval(token: String)
case class RegistrationRejection(token: String)

class RegistrationService(
  private[this] val executionContext: ExecutionContext,
  private[this] val registrationActor: ActorRef,
  private[this] val defaultTimeout: Timeout)
    extends JsonSupport {

  implicit val ec = executionContext
  implicit val t = defaultTimeout

  val route = pathPrefix("registration") {
    pathEnd {
      post {
        handleWith(registration)
      }
    } ~ pathPrefix("request") {
      pathEnd {
        post {
          handleWith(registrationRequest)
        }
      }
    } ~ pathPrefix("approve") {
      pathEnd {
        post {
          handleWith(registrationApprove)
        }
      }
    } ~ pathPrefix("reject") {
      pathEnd {
        post {
          handleWith(registrationReject)
        }
      }
    }
  }

  def registrationRequest(req: RegistrationRequest): Future[RestResponse] = {
    val RegistrationRequest(fname, lname, email) = req
    (registrationActor ? AddRegistration(fname, lname, email)).mapTo[CreateResult[Unit]].map {
      case response: CreateSuccess[Unit] => CreateRestResponse
      case DuplicateValue                => DuplicateError
      case InvalidValue                  => InvalidValueError
    }
  }

  def registrationApprove(req: RegistrationApproval): Future[RestResponse] = {
    val RegistrationApproval(token) = req
    (registrationActor ? ApproveRegistration(token)).mapTo[UpdateResult].map {
      case UpdateSuccess => OkResponse
      case NotFound      => NotFoundError
      case InvalidValue  => InvalidValueError
    }
  }

  def registrationReject(req: RegistrationRejection): Future[RestResponse] = {
    val RegistrationRejection(token) = req
    (registrationActor ? RejectRegistration(token)).mapTo[UpdateResult].map {
      case UpdateSuccess => OkResponse
      case NotFound      => NotFoundError
      case InvalidValue  => InvalidValueError
    }
  }

  def registration(req: Registration): Future[RestResponse] = {
    val Registration(username, fname, lname, email, password, token) = req
    (registrationActor ? RegisterUser(username, fname, lname, email, password, token)).mapTo[CreateResult[String]].map {
      case CreateSuccess(uid) => CreateRestResponse
      case DuplicateValue     => DuplicateError
      case InvalidValue       => InvalidValueError
    }
  }
}
