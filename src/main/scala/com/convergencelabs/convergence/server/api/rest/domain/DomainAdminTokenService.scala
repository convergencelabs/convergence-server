/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is part of the Convergence Server, which is released under
 * the terms of the GNU General Public License version 3 (GPLv3). A copy
 * of the GPLv3 should have been provided along with this file, typically
 * located in the "LICENSE" file, which is part of this source code package.
 * Alternatively, see <https://www.gnu.org/licenses/gpl-3.0.html> for the
 * full text of the GPLv3 license, if it was not provided.
 */

package com.convergencelabs.convergence.server.api.rest.domain

import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.{ActorRef, Scheduler}
import akka.http.scaladsl.marshalling.ToResponseMarshallable.apply
import akka.http.scaladsl.server.Directive.addByNameNullaryApply
import akka.http.scaladsl.server.Directives.{_segmentStringToPathMatcher, complete, get, pathEnd, pathPrefix}
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import com.convergencelabs.convergence.server.api.rest.{InternalServerError, RestResponse, okResponse}
import com.convergencelabs.convergence.server.backend.services.domain.rest.DomainRestActor
import com.convergencelabs.convergence.server.backend.services.domain.rest.DomainRestActor._
import com.convergencelabs.convergence.server.model.DomainId
import com.convergencelabs.convergence.server.security.AuthorizationProfile

import scala.concurrent.{ExecutionContext, Future}

object DomainAdminTokenService {

  case class AdminTokenRestResponse(token: String)

}

private[domain] final class DomainAdminTokenService(domainRestActor: ActorRef[DomainRestActor.Message],
                              scheduler: Scheduler,
                              executionContext: ExecutionContext,
                              timeout: Timeout)
  extends AbstractDomainRestService(scheduler, executionContext, timeout) {

  import DomainAdminTokenService._

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
    domainRestActor
      .ask[AdminTokenResponse](
        r => DomainRestMessage(domain, AdminTokenRequest(username, r)))
      .map(_.token.fold(
        { _ =>
          InternalServerError
        },
        { token =>
          okResponse(AdminTokenRestResponse(token))
        })
      )
  }
}
