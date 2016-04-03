package com.convergencelabs.server.frontend.rest

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.model.HttpEntity
import akka.http.scaladsl.model.ContentTypes

class DomainService {
  val route = pathPrefix("domains") {
    (get & pathEnd) {
      complete {
        HttpEntity(ContentTypes.`text/html(UTF-8)`, "you asked for all of your domains")
      }
    } ~
      (get & path(Segment)) { domainId =>
        complete {
          HttpEntity(ContentTypes.`text/html(UTF-8)`, s"you asked for domain: $domainId")
        }
      }
  }
}