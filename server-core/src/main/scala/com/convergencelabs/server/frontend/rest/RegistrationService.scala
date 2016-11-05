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
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.MediaTypes
import akka.http.scaladsl.model.ContentType
import akka.http.scaladsl.model.HttpCharsets
import akka.http.scaladsl.model.HttpEntity
import akka.http.scaladsl.model.HttpResponse
import com.convergencelabs.templates.html
import com.convergencelabs.server.datastore.RegistrationActor.RegistrationInfoRequest
import com.convergencelabs.server.datastore.RegistrationActor.RegistrationInfo

case class Registration(username: String, fname: String, lname: String, email: String, password: String, token: String)
case class RegistrationRequest(fname: String, lname: String, email: String, reason: String)
case class RegistrationApproval(token: String)
case class RegistrationRejection(token: String)
case class TokenInfo(firstName: String, lastName: String, email: String)
case class TokenInfoResponse(info: Option[TokenInfo]) extends AbstractSuccessResponse

class RegistrationService(
  private[this] val executionContext: ExecutionContext,
  private[this] val registrationActor: ActorRef,
  private[this] val defaultTimeout: Timeout,
  private[this] val registrationBaseUrl: String)
    extends JsonSupport {

  implicit val ec = executionContext
  implicit val t = defaultTimeout

  val route = pathPrefix("registration") {
    pathPrefix("register") {
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
    } ~ pathPrefix("info") {
      pathPrefix(Segment) { token =>
        pathEnd {
          get {
            complete(registrationInfo(token))
          }
        }
      }
    } ~ pathPrefix("approvalForm") {
      pathPrefix(Segment) { token =>
        pathEnd {
          get {
            complete {
              HttpResponse(entity = HttpEntity(
                ContentType.WithCharset(MediaTypes.`text/html`, HttpCharsets.`UTF-8`),
                getApprovalHtml(token)))
            }
          }
        }
      }
    }
  }

  def registrationRequest(req: RegistrationRequest): Future[RestResponse] = {
    val RegistrationRequest(fname, lname, email, reason) = req
    (registrationActor ? AddRegistration(fname, lname, email, reason)).mapTo[CreateResult[Unit]].map {
      case response: CreateSuccess[Unit] => CreateRestResponse
      case DuplicateValue => DuplicateError
      case InvalidValue => InvalidValueError
    }
  }

  def registrationApprove(req: RegistrationApproval): Future[RestResponse] = {
    val RegistrationApproval(token) = req
    (registrationActor ? ApproveRegistration(token)).mapTo[UpdateResult].map {
      case UpdateSuccess => OkResponse
      case NotFound => NotFoundError
      case InvalidValue => InvalidValueError
    }
  }

  def registrationReject(req: RegistrationRejection): Future[RestResponse] = {
    val RegistrationRejection(token) = req
    (registrationActor ? RejectRegistration(token)).mapTo[UpdateResult].map {
      case UpdateSuccess => OkResponse
      case NotFound => NotFoundError
      case InvalidValue => InvalidValueError
    }
  }

  def registration(req: Registration): Future[RestResponse] = {
    val Registration(username, fname, lname, email, password, token) = req
    (registrationActor ? RegisterUser(username, fname, lname, email, password, token)).mapTo[CreateResult[String]].map {
      case CreateSuccess(uid) => CreateRestResponse
      case DuplicateValue => DuplicateError
      case InvalidValue => InvalidValueError
    }
  }

  def registrationInfo(token: String): Future[RestResponse] = {
    println("geting token info for: " + token)
    (registrationActor ? RegistrationInfoRequest(token)).mapTo[Option[RegistrationInfo]].map {
      case Some(RegistrationInfo(fname, lname, email)) =>
        val tokenInfo = TokenInfo(fname, lname, email)
        println("got info: " + tokenInfo)
        (StatusCodes.OK, TokenInfoResponse(Some(tokenInfo)))
      case None =>
        (StatusCodes.OK, TokenInfoResponse(None))
    }
  }

  def getApprovalHtml(token: String): String = {
    val templateHtml = html.registrationApproval(this.registrationBaseUrl, token)
    return templateHtml.toString();
  }
}
