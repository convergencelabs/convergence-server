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

import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.http.scaladsl.marshalling.ToResponseMarshallable.apply
import akka.http.scaladsl.server.Directive.{addByNameNullaryApply, addDirectiveApply}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import com.convergencelabs.convergence.server.api.rest.{OkResponse, RestResponse, okResponse}
import com.convergencelabs.convergence.server.datastore.domain.ConfigStoreActor.{GetModelSnapshotPolicyResponse, _}
import com.convergencelabs.convergence.server.domain.rest.DomainRestActor.{DomainRestMessage, Message}
import com.convergencelabs.convergence.server.domain.{DomainId, ModelSnapshotConfig}
import com.convergencelabs.convergence.server.security.AuthorizationProfile

import scala.concurrent.{ExecutionContext, Future}

class DomainConfigService(private[this] val domainRestActor: ActorRef[Message],
                          private[this] val system: ActorSystem[_],
                          private[this] val executionContext: ExecutionContext,
                          private[this] val timeout: Timeout)
  extends AbstractDomainRestService(system, executionContext, timeout) {

  import DomainConfigService._

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

  private[this] def getAnonymousAuthEnabled(domain: DomainId): Future[RestResponse] = {
    domainRestActor.ask[GetAnonymousAuthResponse](r => DomainRestMessage(domain, GetAnonymousAuthRequest(r))).flatMap {
      case GetAnonymousAuthSuccess(enabled) =>
        Future.successful(okResponse(AnonymousAuthResponseData(enabled)))
      case RequestFailure(cause) =>
        Future.failed(cause)
    }
  }

  private[this] def setAnonymousAuthEnabled(domain: DomainId, request: AnonymousAuthPut): Future[RestResponse] = {
    domainRestActor.ask[SetAnonymousAuthResponse](r => DomainRestMessage(domain, SetAnonymousAuthRequest(request.enabled, r))).flatMap {
      case RequestSuccess() =>
        Future.successful(OkResponse)
      case RequestFailure(cause) =>
        Future.failed(cause)
    }
  }

  private[this] def getModelSnapshotPolicy(domain: DomainId): Future[RestResponse] = {
    domainRestActor.ask[GetModelSnapshotPolicyResponse](r => DomainRestMessage(domain, GetModelSnapshotPolicyRequest(r))).flatMap {
      case GetModelSnapshotPolicySuccess(policy) =>
        val ModelSnapshotConfig(
        snapshotsEnabled,
        triggerByVersion,
        limitByVersion,
        minimumVersionInterval,
        maximumVersionInterval,
        triggerByTime,
        limitByTime,
        minimumTimeInterval,
        maximumTimeInterval) = policy
        val responseData = ModelSnapshotPolicyData(
          snapshotsEnabled,
          triggerByVersion,
          maximumVersionInterval,
          limitByVersion,
          minimumVersionInterval,
          triggerByTime,
          maximumTimeInterval.toMillis,
          limitByTime,
          minimumTimeInterval.toMillis)
        Future.successful(okResponse(responseData))
      case RequestFailure(cause) =>
        Future.failed(cause)
    }
  }

  private[this] def setModelSnapshotPolicy(domain: DomainId, policyData: ModelSnapshotPolicyData): Future[RestResponse] = {
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

    domainRestActor.ask[SetModelSnapshotPolicyResponse](r => DomainRestMessage(domain, SetModelSnapshotPolicyRequest(policy, r))).flatMap {
      case RequestSuccess() =>
        Future.successful(OkResponse)
      case RequestFailure(cause) =>
        Future.failed(cause)
    }
  }
}

object DomainConfigService {

  case class AnonymousAuthPut(enabled: Boolean)

  case class AnonymousAuthResponseData(enabled: Boolean)

  case class ModelSnapshotPolicyData(snapshotsEnabled: Boolean,
                                     triggerByVersion: Boolean,
                                     maximumVersionInterval: Long,
                                     limitByVersion: Boolean,
                                     minimumVersionInterval: Long,
                                     triggerByTime: Boolean,
                                     maximumTimeInterval: Long,
                                     limitByTime: Boolean,
                                     minimumTimeInterval: Long)
}
