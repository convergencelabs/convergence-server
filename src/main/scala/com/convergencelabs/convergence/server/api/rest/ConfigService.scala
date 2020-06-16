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
import akka.actor.typed.{ActorRef, Scheduler}
import akka.http.scaladsl.marshalling.ToResponseMarshallable.apply
import akka.http.scaladsl.server.Directive.{addByNameNullaryApply, addDirectiveApply}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import com.convergencelabs.convergence.server.datastore.convergence.ConfigStoreActor._
import com.convergencelabs.convergence.server.security.{AuthorizationProfile, Permissions}
import grizzled.slf4j.Logging

import scala.concurrent.{ExecutionContext, Future}

object ConfigService {
}

private[rest] class ConfigService(configActor: ActorRef[Message],
                                  scheduler: Scheduler,
                                  executionContext: ExecutionContext,
                                  defaultTimeout: Timeout)
  extends JsonSupport with Logging {

  private[this] implicit val ec: ExecutionContext = executionContext
  private[this] implicit val t: Timeout = defaultTimeout
  private[this] implicit val s: Scheduler = scheduler

  val route: AuthorizationProfile => Route = { authProfile: AuthorizationProfile =>
    pathPrefix("config") {
      pathEnd {
        get {
          parameters(_string2NR("keys").*) { keys =>
            complete(handleGetAllConfigs(keys))
          }
        } ~ post {
          authorize(canManageSettings(authProfile)) {
            entity(as[Map[String, Any]]) { configs =>
              complete(handleSetConfigs(configs))
            }
          }
        }
      } ~ path("app") {
        complete(handleGetAppConfigs())
      }
    }
  }

  private[this] def handleGetAllConfigs(keys: Iterable[String]): Future[RestResponse] = {
    val keyFilter = keys.toList match {
      case Nil => None
      case k => Some(k)
    }
    configActor
      .ask[GetConfigsResponse](GetConfigsRequest(keyFilter, _))
      .map(_.configs.fold(
        {
          case UnknownError() =>
            InternalServerError
        },
        { configs =>
          okResponse(configs)
        })
      )
  }

  private[this] def handleGetAppConfigs(): Future[RestResponse] = {
    // FIXME request specific keys
    configActor
      .ask[GetConfigsResponse](GetConfigsRequest(None, _))
      .map(_.configs.fold(
        {
          case UnknownError() =>
            InternalServerError
        },
        { configs =>
          okResponse(configs)
        })
      )
  }

  private[this] def handleSetConfigs(configs: Map[String, Any]): Future[RestResponse] = {
    configActor.ask[SetConfigsResponse](SetConfigsRequest(configs, _))
      .map(_.response.fold(
        {
          case UnknownError() =>
            InternalServerError
        },
        { configs =>
          okResponse(configs)
        })
      )
  }

  private[this] def canManageSettings(authProfile: AuthorizationProfile): Boolean = {
    authProfile.hasGlobalPermission(Permissions.Global.ManageSettings)
  }
}
