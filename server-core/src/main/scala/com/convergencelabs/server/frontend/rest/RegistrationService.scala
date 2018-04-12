package com.convergencelabs.server.frontend.rest

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.language.postfixOps

import com.convergencelabs.server.datastore.RegistrationActor.ApproveRegistration
import com.convergencelabs.server.datastore.RegistrationActor.RegisterUser
import com.convergencelabs.server.datastore.RegistrationActor.RegistrationInfo
import com.convergencelabs.server.datastore.RegistrationActor.RegistrationInfoRequest
import com.convergencelabs.server.datastore.RegistrationActor.RejectRegistration
import com.convergencelabs.server.datastore.RegistrationActor.RequestRegistration
import com.convergencelabs.templates.html

import akka.actor.ActorRef
import akka.http.scaladsl.model.ContentType
import akka.http.scaladsl.model.HttpCharsets
import akka.http.scaladsl.model.HttpEntity
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.model.MediaTypes
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives.Segment
import akka.http.scaladsl.server.Directives._enhanceRouteWithConcatenation
import akka.http.scaladsl.server.Directives._segmentStringToPathMatcher
import akka.http.scaladsl.server.Directives.complete
import akka.http.scaladsl.server.Directives.get
import akka.http.scaladsl.server.Directives.handleWith
import akka.http.scaladsl.server.Directives.pathEnd
import akka.http.scaladsl.server.Directives.pathPrefix
import akka.http.scaladsl.server.Directives.path
import akka.http.scaladsl.server.Directives.post
import akka.pattern.ask
import akka.util.Timeout
import grizzled.slf4j.Logging

case class Registration(username: String, fname: String, lname: String, email: String, password: String, token: String)
case class RegistrationRequest(fname: String, lname: String, email: String, company: Option[String], title: Option[String], reason: String)
case class RegistrationApproval(token: String)
case class RegistrationRejection(token: String)
case class TokenInfo(firstName: String, lastName: String, email: String)
case class TokenInfoResponse(info: Option[TokenInfo]) extends AbstractSuccessResponse

class RegistrationService(
  private[this] val executionContext: ExecutionContext,
  private[this] val registrationActor: ActorRef,
  private[this] val defaultTimeout: Timeout,
  private[this] val registrationBaseUrl: String)
    extends JsonSupport
    with Logging {

  implicit val ec = executionContext
  implicit val t = defaultTimeout

  val route = pathPrefix("registration") {
    (path("register") & post) {
      handleWith(registration)
    } ~ (path("request") & post) {
      handleWith(registrationRequest)
    } ~ (path("approve") & post) {
      handleWith(registrationApprove)
    } ~ (path("reject") & post) {
      handleWith(registrationReject)
    } ~ (path("info" / Segment) & get) { token =>
      complete(registrationInfo(token))
    } ~ (path("approvalForm" / Segment) & get) { token =>
      complete {
        HttpResponse(entity = HttpEntity(
          ContentType.WithCharset(MediaTypes.`text/html`, HttpCharsets.`UTF-8`),
          getApprovalHtml(token)))
      }
    }
  }

  def registrationRequest(req: RegistrationRequest): Future[RestResponse] = {
    val RegistrationRequest(fname, lname, email, company, title, reason) = req
    logger.debug(s"Received a registration request for: ${email}")
    val message = RequestRegistration(fname, lname, email, company, title, reason)

    (registrationActor ? message) map { _ => CreateRestResponse }
  }

  def registrationApprove(req: RegistrationApproval): Future[RestResponse] = {
    val RegistrationApproval(token) = req
    logger.debug(s"Received a registration approval for: ${token}")
    val message = ApproveRegistration(token)
    (registrationActor ? message) map { _ => OkResponse }
  }

  def registrationReject(req: RegistrationRejection): Future[RestResponse] = {
    val RegistrationRejection(token) = req
    logger.debug(s"Received a registration rejection for: ${token}")
    val message = RejectRegistration(token)
    (registrationActor ? message) map { _ => OkResponse }
  }

  def registration(req: Registration): Future[RestResponse] = {
    val Registration(username, fname, lname, email, password, token) = req
    logger.debug(s"Received a registration for '${username}' with token: ${token}")
    val message = RegisterUser(username, fname, lname, email, password, token)
    (registrationActor ? message) map { _ => CreateRestResponse }
  }

  def registrationInfo(token: String): Future[RestResponse] = {
    logger.debug(s"Received an info request for registration token: ${token}")
    val message = RegistrationInfoRequest(token)
    (registrationActor ? message).mapTo[Option[RegistrationInfo]].map {
      case Some(RegistrationInfo(fname, lname, email)) =>
        val tokenInfo = TokenInfo(fname, lname, email)
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
