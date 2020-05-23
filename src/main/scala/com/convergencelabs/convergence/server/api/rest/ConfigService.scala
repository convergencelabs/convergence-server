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

import akka.actor.ActorRef
import akka.http.scaladsl.marshalling.ToResponseMarshallable.apply
import akka.http.scaladsl.server.Directive.{addByNameNullaryApply, addDirectiveApply}
import akka.http.scaladsl.server.Directives.{_enhanceRouteWithConcatenation, _segmentStringToPathMatcher, _string2NR, as, complete, entity, get, parameters, path, pathEnd, pathPrefix, post}
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import akka.util.Timeout
import com.convergencelabs.convergence.server.datastore.convergence.ConfigStoreActor.{GetConfigsRequest, GetConfigsResponse, SetConfigsRequest}
import com.convergencelabs.convergence.server.security.AuthorizationProfile
import grizzled.slf4j.Logging

import scala.concurrent.{ExecutionContext, Future}

object ConfigService {
}

class ConfigService(private[this] val executionContext: ExecutionContext,
                    private[this] val configActor: ActorRef,
                    private[this] val defaultTimeout: Timeout)
  extends JsonSupport
    with Logging {

  private[this] implicit val ec: ExecutionContext = executionContext
  private[this] implicit val t: Timeout = defaultTimeout

  val route: AuthorizationProfile => Route = { authProfile: AuthorizationProfile =>
    pathPrefix("config") {
      pathEnd {
        get {
          parameters(_string2NR("keys").*) { keys =>
            complete(getAllConfigs(authProfile, keys))
          }
        } ~ post {
          entity(as[Map[String, Any]]) { configs =>
            complete(setConfigs(authProfile, configs))
          }
        }
      } ~ path("app") {
        complete(getAppConfigs(authProfile))
      }
    }
  }

  private[this] def getAllConfigs(authProfile: AuthorizationProfile, keys: Iterable[String]): Future[RestResponse] = {
    val keyFilter = keys.toList match {
      case Nil => None
      case k => Some(k)
    }
    val message = GetConfigsRequest(keyFilter)
    (configActor ? message).mapTo[GetConfigsResponse].map(_.configs).map(okResponse(_))
  }

  private[this] def getAppConfigs(authProfile: AuthorizationProfile): Future[RestResponse] = {
    // FIXME request specific keys
    val message = GetConfigsRequest(None)
    (configActor ? message).mapTo[GetConfigsResponse].map(_.configs).map(okResponse(_))
  }

  private[this] def setConfigs(authProfile: AuthorizationProfile, configs: Map[String, Any]): Future[RestResponse] = {
    val message = SetConfigsRequest(configs)
    (configActor ? message).mapTo[Unit].map(_ => OkResponse)
  }
}
