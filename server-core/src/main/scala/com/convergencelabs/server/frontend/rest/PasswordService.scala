package com.convergencelabs.server.frontend.rest

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import com.convergencelabs.server.datastore.ConvergenceUserManagerActor.GetConvergenceUser

import akka.actor.ActorRef
import akka.http.scaladsl.marshalling.ToResponseMarshallable.apply
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directive.addByNameNullaryApply
import akka.http.scaladsl.server.Directive.addDirectiveApply
import akka.http.scaladsl.server.Directives._segmentStringToPathMatcher
import akka.http.scaladsl.server.Directives._enhanceRouteWithConcatenation
import akka.http.scaladsl.server.Directives.as
import akka.http.scaladsl.server.Directives.entity
import akka.http.scaladsl.server.Directives.complete
import akka.http.scaladsl.server.Directives.post
import akka.http.scaladsl.server.Directives.pathEnd
import akka.http.scaladsl.server.Directives._
import akka.pattern.ask
import akka.util.Timeout
import com.convergencelabs.server.datastore.ConvergenceUserManagerActor.SetPasswordRequest
import grizzled.slf4j.Logging

case class PasswordSetRequest(password: String)

class PasswordService(
  private[this] val executionContext: ExecutionContext,
  private[this] val convergenceUserActor: ActorRef,
  private[this] val defaultTimeout: Timeout)
    extends JsonSupport
    with Logging {

  implicit val ec = executionContext
  implicit val t = defaultTimeout

  val route = { username: String =>
    (path("password") & post) {
      entity(as[PasswordSetRequest]) { password =>
        complete(setPassword(username, password))
      }
    }
  }

  def setPassword(username: String, request: PasswordSetRequest): Future[RestResponse] = {
    logger.debug(s"Received request to set the password for user: ${username}")
    val PasswordSetRequest(password) = request
    val message = SetPasswordRequest(username, password)
    (convergenceUserActor ? message) map { _ => OkResponse }
  }
}
