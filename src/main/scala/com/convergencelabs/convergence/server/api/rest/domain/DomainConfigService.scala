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

import java.time.Duration

import akka.actor.ActorRef
import akka.http.scaladsl.marshalling.ToResponseMarshallable.apply
import akka.http.scaladsl.server.Directive.{addByNameNullaryApply, addDirectiveApply}
import akka.http.scaladsl.server.Directives.{_enhanceRouteWithConcatenation, _segmentStringToPathMatcher, as, authorize, complete, entity, get, path, pathPrefix, put}
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import com.convergencelabs.convergence.server.api.rest.{OkResponse, RestResponse, okResponse}
import com.convergencelabs.convergence.server.datastore.domain.ConfigStoreActor.{GetAnonymousAuth, GetModelSnapshotPolicy, SetAnonymousAuth, SetModelSnapshotPolicy}
import com.convergencelabs.convergence.server.domain.{DomainId, ModelSnapshotConfig}
import com.convergencelabs.convergence.server.domain.rest.RestDomainActor.DomainRestMessage
import com.convergencelabs.convergence.server.security.AuthorizationProfile

import scala.concurrent.{ExecutionContext, Future}

object DomainConfigService {
  case class AnonymousAuthPut(enabled: Boolean)
  case class AnonymousAuthResponse(enabled: Boolean)
  case class ModelSnapshotPolicyData(
    snapshotsEnabled: Boolean,
    triggerByVersion: Boolean,
    maximumVersionInterval: Long,
    limitByVersion: Boolean,
    minimumVersionInterval: Long,
    triggerByTime: Boolean,
    maximumTimeInterval: Long,
    limitByTime: Boolean,
    minimumTimeInterval: Long)
}

class DomainConfigService(
  private[this] val executionContext: ExecutionContext,
  private[this] val timeout: Timeout,
  private[this] val domainRestActor: ActorRef)
  extends DomainRestService(executionContext, timeout) {

  import DomainConfigService._
  import akka.pattern.ask

  def route(authProfile: AuthorizationProfile, domain: DomainId): Route = {
    pathPrefix("config") {
      path("anonymousAuth") {
        get {
          authorize(canManageSettings(domain, authProfile)) {
            complete(getAnonymousAuthEnabled(domain))
          }
        } ~ put {
          entity(as[AnonymousAuthPut]) { request =>
            complete(setAnonymousAuthEnabled(domain, request))
          }
        }
      } ~
        path("modelSnapshotPolicy") {
          get {
            complete(getModelSnapshotPolicy(domain))
          } ~ put {
            entity(as[ModelSnapshotPolicyData]) { policyData =>
              authorize(canManageSettings(domain, authProfile)) {
                complete(setModelSnapshotPolicy(domain, policyData))
              }
            }
          }
        }
    }
  }

  def getAnonymousAuthEnabled(domain: DomainId): Future[RestResponse] = {
    val message = DomainRestMessage(domain, GetAnonymousAuth)
    (domainRestActor ? message).mapTo[Boolean] map
      (enabled => okResponse(AnonymousAuthResponse(enabled)))
  }

  def setAnonymousAuthEnabled(domain: DomainId, request: AnonymousAuthPut): Future[RestResponse] = {
    val message = DomainRestMessage(domain, SetAnonymousAuth(request.enabled))
    (domainRestActor ? message) map (_ => OkResponse)
  }

  def getModelSnapshotPolicy(domain: DomainId): Future[RestResponse] = {
    val message = DomainRestMessage(domain, GetModelSnapshotPolicy)
    (domainRestActor ? message).mapTo[ModelSnapshotConfig] map { config =>
      val ModelSnapshotConfig(
        snapshotsEnabled,
        triggerByVersion,
        limitByVersion,
        minimumVersionInterval,
        maximumVersionInterval,
        triggerByTime,
        limitByTime,
        minimumTimeInterval,
        maximumTimeInterval) = config
      okResponse(ModelSnapshotPolicyData(
        snapshotsEnabled,
        triggerByVersion,
        maximumVersionInterval,
        limitByVersion,
        minimumVersionInterval,
        triggerByTime,
        maximumTimeInterval.toMillis,
        limitByTime,
        minimumTimeInterval.toMillis))
    }
  }

  def setModelSnapshotPolicy(domain: DomainId, policyData: ModelSnapshotPolicyData): Future[RestResponse] = {
    val ModelSnapshotPolicyData(
      snapshotsEnabled,
      triggerByVersion,
      maximumVersionInterval,
      limitByVersion,
      minimumVersionInterval,
      triggerByTime,
      maximumTimeInterval,
      limitByTime,
      minimumTimeInterval
      ) = policyData

    val policy =
      ModelSnapshotConfig(
        snapshotsEnabled,
        triggerByVersion,
        limitByVersion,
        minimumVersionInterval,
        maximumVersionInterval,
        triggerByTime,
        limitByTime,
        Duration.ofMillis(minimumTimeInterval),
        Duration.ofMillis(maximumTimeInterval))

    val message = DomainRestMessage(domain, SetModelSnapshotPolicy(policy))
    (domainRestActor ? message) map (_ => OkResponse)
  }
}
