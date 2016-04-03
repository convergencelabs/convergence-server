package com.convergencelabs.server.frontend.rest

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.model.HttpEntity
import akka.http.scaladsl.model.ContentTypes

class AuthService {
  val route = pathPrefix("auth") {
    (get & pathEnd) {
      complete {
        HttpEntity(ContentTypes.`text/html(UTF-8)`, "you asked for a token")
      }
    } 
  }
}
