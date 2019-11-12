/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

package com.convergencelabs.server.api.rest

import akka.actor.ActorRef
import akka.http.scaladsl.marshalling.ToResponseMarshallable.apply
import akka.http.scaladsl.server.Directive.{addByNameNullaryApply, addDirectiveApply}
import akka.http.scaladsl.server.Directives.{_enhanceRouteWithConcatenation, _segmentStringToPathMatcher, _string2NR, as, complete, entity, get, parameters, path, pathEnd, pathPrefix, post}
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import com.convergencelabs.server.datastore.convergence.ConfigStoreActor.{GetConfigs, SetConfigs}
import com.convergencelabs.server.security.AuthorizationProfile
import grizzled.slf4j.Logging

import scala.concurrent.{ExecutionContext, Future}

object ConfigService {
}

class ConfigService(
  private[this] val executionContext: ExecutionContext,
  private[this] val configActor: ActorRef,
  private[this] val defaultTimeout: Timeout)
  extends JsonSupport
  with Logging {

  import akka.pattern.ask

  implicit val ec: ExecutionContext = executionContext
  implicit val t: Timeout = defaultTimeout

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

  def getAllConfigs(authProfile: AuthorizationProfile, keys: Iterable[String]): Future[RestResponse] = {
    val keyFilter = keys.toList match {
      case Nil => None
      case k => Some(k)
    }
    val message = GetConfigs(keyFilter)
    (configActor ? message).mapTo[Map[String, Any]].map(okResponse(_))
  }

  def getAppConfigs(authProfile: AuthorizationProfile): Future[RestResponse] = {
    // FIXME request specific keys
    val message = GetConfigs(None)
    (configActor ? message).mapTo[Map[String, Any]].map(okResponse(_))
  }

  def setConfigs(authProfile: AuthorizationProfile, configs: Map[String, Any]): Future[RestResponse] = {
    val message = SetConfigs(configs)
    (configActor ? message).mapTo[Unit].map(_ => OkResponse)
  }
}
