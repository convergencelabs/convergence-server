package com.convergencelabs.server.frontend.rest

import akka.http.scaladsl.server.directives.{ RouteDirectives, BasicDirectives, ParameterDirectives, FutureDirectives }
import akka.http.scaladsl.server.Directive1
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.AuthenticationFailedRejection
import akka.http.scaladsl.server.AuthorizationFailedRejection
import akka.http.scaladsl.model.StatusCodes

object AuthenticateDirectives {
  import BasicDirectives._
  import RouteDirectives._
  import FutureDirectives._
  import ParameterDirectives._

  def requireAuthenticated: Directive1[String] = {
    val result: Directive1[String] = parameter('token).flatMap { token =>
      val authResponse: Option[Unit] = Some(())
      authResponse match {
        case Some(user) => provide("uerid")
        case None => reject()
      }
    }
    result.recover { x => complete((StatusCodes.Forbidden, "You're out of your depth!")) }
  }
}
