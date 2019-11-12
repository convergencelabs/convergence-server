/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

package com.convergencelabs.server.api.rest.domain

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import com.convergencelabs.server.api.rest.RestResponse
import com.convergencelabs.server.api.rest.okResponse
import com.convergencelabs.server.domain.DomainId
import com.convergencelabs.server.domain.rest.RestDomainActor.AdminTokenRequest
import com.convergencelabs.server.domain.rest.RestDomainActor.DomainRestMessage
import com.convergencelabs.server.security.AuthorizationProfile

import akka.actor.ActorRef
import akka.http.scaladsl.marshalling.ToResponseMarshallable.apply
import akka.http.scaladsl.server.Directive.addByNameNullaryApply
import akka.http.scaladsl.server.Directives._segmentStringToPathMatcher
import akka.http.scaladsl.server.Directives.complete
import akka.http.scaladsl.server.Directives.get
import akka.http.scaladsl.server.Directives.pathEnd
import akka.http.scaladsl.server.Directives.pathPrefix
import akka.http.scaladsl.server.Route
import akka.util.Timeout

object DomainAdminTokenService {
  case class AdminTokenRestResponse(token: String)
}

class DomainAdminTokenService(
  executionContext: ExecutionContext,
  timeout: Timeout,
  private[this] val domainRestActor: ActorRef)
  extends DomainRestService(executionContext, timeout) {

  import DomainAdminTokenService._
  import akka.pattern.ask

  def route(authProfile: AuthorizationProfile, domain: DomainId): Route = {
    pathPrefix("convergenceUserToken") {
      pathEnd {
        get {
          complete(getConvergenceUserToken(domain, authProfile.username))
        }
      }
    }
  }

  def getConvergenceUserToken(domain: DomainId, username: String): Future[RestResponse] = {
    val message = DomainRestMessage(domain, AdminTokenRequest(username))
    (domainRestActor ? message).mapTo[String] map {
      case token: String => okResponse(AdminTokenRestResponse(token))
    }
  }
}
