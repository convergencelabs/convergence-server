package com.convergencelabs.server.frontend.rest

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import com.convergencelabs.server.datastore.AuthStoreActor.AuthFailure
import com.convergencelabs.server.datastore.AuthStoreActor.AuthRequest
import com.convergencelabs.server.datastore.AuthStoreActor.AuthResponse
import com.convergencelabs.server.datastore.AuthStoreActor.AuthSuccess

import akka.actor.ActorRef
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directive.addByNameNullaryApply
import akka.http.scaladsl.server.Directives.handleWith
import akka.http.scaladsl.server.Directives.pathEnd
import akka.http.scaladsl.server.Directives.pathPrefix
import akka.http.scaladsl.server.Directives.post
import akka.http.scaladsl.server.Directives.segmentStringToPathMatcher
import akka.pattern.ask
import akka.util.Timeout
import akka.http.scaladsl.server.Directives.complete

class ConvergenceAdminService() extends JsonSupport {

  val route = { adminUser: String =>
    pathEnd {
      complete(s"User is '$adminUser'")
    }
  }
}
