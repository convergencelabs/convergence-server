package com.convergencelabs.server.frontend.rest

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.model.HttpEntity
import akka.http.scaladsl.model.ContentTypes


// Read this to start thinking about how to forward to actors
// https://github.com/eigengo/activator-akka-spray/blob/master/src/main/scala/api/RegistrationService.scala
//
// probably want to use this:
//  https://github.com/hseeberger/akka-http-json
// for json4s support as outlined here: http://danielasfregola.com/2015/08/17/spray-how-to-deserialize-entities-with-json4s/
// and: http://danielasfregola.com/2016/02/07/how-to-build-a-rest-api-with-akka-http/
//
class AuthService {
  val route = pathPrefix("auth") {
    (get & pathEnd) {
      complete {
        HttpEntity(ContentTypes.`text/html(UTF-8)`, "you asked for a token")
      }
    } 
  }
}
