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

package com.convergencelabs.convergence.server.api.rest

import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.http.scaladsl.marshalling.ToResponseMarshallable.apply
import akka.http.scaladsl.server.Directive.addByNameNullaryApply
import akka.http.scaladsl.server.Directives.{_segmentStringToPathMatcher, complete, get, pathEnd, pathPrefix}
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import com.convergencelabs.convergence.server.datastore.convergence.ServerStatusActor._
import com.convergencelabs.convergence.server.security.AuthorizationProfile
import grizzled.slf4j.Logging

import scala.concurrent.{ExecutionContext, Future}

private[rest] object ServerStatusService {

  case class ServerStatus(version: String, distribution: String, status: String, namespaces: Long, domains: Long)

}

private[rest] class ServerStatusService(private[this] val statusActor: ActorRef[Message],
                                        private[this] val system: ActorSystem[_],
                                        private[this] val executionContext: ExecutionContext,
                                        private[this] val defaultTimeout: Timeout)
  extends JsonSupport with Logging {

  import ServerStatusService._

  private[this] implicit val ec: ExecutionContext = executionContext
  private[this] implicit val t: Timeout = defaultTimeout
  private[this] implicit val s: ActorSystem[_] = system


  val route: AuthorizationProfile => Route = { authProfile: AuthorizationProfile =>
    pathPrefix("status") {
      pathEnd {
        get {
          complete(getServerStatus(authProfile))
        }
      }
    }
  }

  private[this] def getServerStatus(authProfile: AuthorizationProfile): Future[RestResponse] = {
    statusActor.ask[GetStatusResponse](GetStatusRequest)
      .map(_.status.fold(
        {
          case UnknownError() =>
            InternalServerError
        },
        {
          case ServerStatusResponse(version, distribution, serverStatus, namespaces, domains)=>
            okResponse(ServerStatus(version, distribution, serverStatus, namespaces, domains))
        })
      )
  }
}
