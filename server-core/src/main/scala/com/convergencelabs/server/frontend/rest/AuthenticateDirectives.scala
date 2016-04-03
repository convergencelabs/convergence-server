package com.convergencelabs.server.frontend.rest

import akka.http.scaladsl.server.directives.{ RouteDirectives, BasicDirectives, HeaderDirectives, FutureDirectives }
import akka.http.scaladsl.server.Directive1

trait AuthenticateDirectives {
  import BasicDirectives._
  import HeaderDirectives._
  import RouteDirectives._
  import FutureDirectives._

  def authenticate: Directive1[String] = {
    val authResponse: Option[Unit] = Some(())
    authResponse match {
      case Some(user) => provide("")
      case None => reject
    }
  }
}