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
import akka.http.scaladsl.server.Directive.{addByNameNullaryApply, addDirectiveApply}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import com.convergencelabs.convergence.server.api.rest._
import com.convergencelabs.convergence.server.backend.services.domain.config.ConfigStoreActor._
import com.convergencelabs.convergence.server.backend.services.domain.rest.DomainRestActor.{DomainRestMessage, Message}
import com.convergencelabs.convergence.server.model
import com.convergencelabs.convergence.server.model.DomainId
import com.convergencelabs.convergence.server.model.domain.{CollectionConfig, ModelSnapshotConfig}
import com.convergencelabs.convergence.server.security.AuthorizationProfile

import java.time.Duration
import scala.concurrent.{ExecutionContext, Future}

private[domain] final class DomainConfigService(domainRestActor: ActorRef[Message],
                                                scheduler: Scheduler,
                                                executionContext: ExecutionContext,
                                                timeout: Timeout)
  extends AbstractDomainRestService(scheduler, executionContext, timeout) {

  import DomainConfigService._

  def route(authProfile: AuthorizationProfile, domain: DomainId): Route = {
    pathPrefix("config") {
      path("anonymousAuth") {
        get {
          authorize(canManageDomainSettings(domain, authProfile)) {
            complete(getAnonymousAuthEnabled(domain))
          }
        } ~ put {
          entity(as[AnonymousAuthPut]) { request =>
            complete(setAnonymousAuthEnabled(domain, request))
          }
        }
      } ~ path("modelSnapshotPolicy") {
        get {
          complete(getModelSnapshotPolicy(domain))
        } ~ put {
          authorize(canManageDomainSettings(domain, authProfile)) {
            entity(as[ModelSnapshotPolicyData]) { policyData =>
              complete(setModelSnapshotPolicy(domain, policyData))
            }
          }
        }
      } ~ path("collection") {
        get {
          complete(getCollectionConfig(domain))
        } ~ put {
          authorize(canManageDomainSettings(domain, authProfile)) {
            entity(as[CollectionConfigData]) { config =>
              complete(setCollectionConfig(domain, config))
            }
          }
        }
      } ~ path("reconnect") {
        get {
          complete(getReconnectConfig(domain))
        } ~ put {
          authorize(canManageDomainSettings(domain, authProfile)) {
            entity(as[ReconnectConfigData]) { config =>
              complete(setReconnectConfig(domain, config))
            }
          }
        }
      }
    }
  }

  private[this] def getAnonymousAuthEnabled(domain: DomainId): Future[RestResponse] = {
    domainRestActor
      .ask[GetAnonymousAuthResponse](r => DomainRestMessage(domain, GetAnonymousAuthRequest(r)))
      .map(_.enabled.fold(
        {
          case UnknownError() =>
            InternalServerError
        },
        { enabled =>
          okResponse(AnonymousAuthResponseData(enabled))
        }
      ))
  }

  private[this] def setAnonymousAuthEnabled(domain: DomainId, request: AnonymousAuthPut): Future[RestResponse] = {
    domainRestActor
      .ask[SetAnonymousAuthResponse](r => DomainRestMessage(domain, SetAnonymousAuthRequest(request.enabled, r)))
      .map(_.response.fold(
        {
          case UnknownError() =>
            InternalServerError
        },
        { _ =>
          OkResponse
        }
      ))
  }

  private[this] def getModelSnapshotPolicy(domain: DomainId): Future[RestResponse] = {
    domainRestActor
      .ask[GetModelSnapshotPolicyResponse](r => DomainRestMessage(domain, GetModelSnapshotPolicyRequest(r)))
      .map(_.policy.fold(
        {
          case UnknownError() =>
            InternalServerError
        },
        { policy =>
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
          okResponse(responseData)
        }
      ))
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
      model.domain.ModelSnapshotConfig(
        snapshotsEnabled,
        triggerByVersion,
        limitByVersion,
        minimumVersionInterval,
        maximumVersionInterval,
        triggerByTime,
        limitByTime,
        Duration.ofMillis(minimumTimeInterval),
        Duration.ofMillis(maximumTimeInterval))

    domainRestActor
      .ask[SetModelSnapshotPolicyResponse](
        r => DomainRestMessage(domain, SetModelSnapshotPolicyRequest(policy, r)))
      .map(_.response.fold(
        {
          case UnknownError() =>
            InternalServerError
        },
        { _ =>
          OkResponse
        }
      ))
  }

  private[this] def getCollectionConfig(domain: DomainId): Future[RestResponse] = {
    domainRestActor
      .ask[GetCollectionConfigResponse](r => DomainRestMessage(domain, GetCollectionConfigRequest(r)))
      .map(_.response.fold(
        { _ =>
          InternalServerError
        }, { case CollectionConfig(autoCreate) =>
          okResponse(CollectionConfigData(autoCreate))
        }
      ))
  }

  private[this] def setCollectionConfig(domain: DomainId, configData: CollectionConfigData): Future[RestResponse] = {
    val CollectionConfigData(autoCreate) = configData
    val config = CollectionConfig(autoCreate)
    domainRestActor
      .ask[SetCollectionConfigResponse](r => DomainRestMessage(domain, SetCollectionConfigRequest(config, r)))
      .map(_.response.fold(
        { _ =>
          InternalServerError
        }, { _ =>
          OkResponse
        }
      ))
  }

  private[this] def getReconnectConfig(domain: DomainId): Future[RestResponse] = {
    domainRestActor
      .ask[GetReconnectConfigResponse](r => DomainRestMessage(domain, GetReconnectConfigRequest(r)))
      .map(_.response.fold(
        { _ => InternalServerError },
        { config => okResponse(ReconnectConfigData(config.tokenValidity)) }
      ))
  }

  private[this] def setReconnectConfig(domain: DomainId, configData: ReconnectConfigData): Future[RestResponse] = {
    val ReconnectConfigData(minutes) = configData
    domainRestActor
      .ask[SetReconnectConfigResponse](r =>
        DomainRestMessage(domain, SetReconnectConfigRequest(ReconnectConfig(minutes), r)))
      .map(_.response.fold(
        { _ => InternalServerError },
        { _ => OkResponse }
      ))
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

  case class CollectionConfigData(autoCreate: Boolean)

  case class ReconnectConfigData(tokenValidity: Long)

}
