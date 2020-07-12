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
import com.convergencelabs.convergence.server.api.rest.DatabaseManagerRestService.{UpgradeRequest, VersionResponse}
import com.convergencelabs.convergence.server.backend.services.server.DatabaseManagerActor
import com.convergencelabs.convergence.server.backend.services.server.DatabaseManagerActor._
import com.convergencelabs.convergence.server.model.DomainId
import com.convergencelabs.convergence.server.security.AuthorizationProfile
import grizzled.slf4j.Logging

import scala.concurrent.{ExecutionContext, Future}

private[rest] final class DatabaseManagerRestService(executionContext: ExecutionContext,
                                               scheduler: Scheduler,
                                               databaseManager: ActorRef[DatabaseManagerActor.Message],
                                               defaultTimeout: Timeout)
  extends JsonSupport with Logging with PermissionChecks {

  private[this] implicit val ec: ExecutionContext = executionContext
  private[this] implicit val t: Timeout = defaultTimeout
  private[this] implicit val s: Scheduler = scheduler

  val route: AuthorizationProfile => Route = { authProfile: AuthorizationProfile =>
    pathPrefix("schema") {
      (post & pathPrefix("upgrade")) {
        path("convergence") {
          authorize(isServerAdmin(authProfile)) {
            handleWith(upgradeConvergence)
          }
        } ~ path("domains") {
          authorize(isServerAdmin(authProfile)) {
            complete(upgradeDomains())
          }
        } ~ path("domain" / Segment / Segment) { (namespace, domainId) =>
          authorize(canManageDomain(DomainId(namespace, domainId), authProfile)) {
            entity(as[UpgradeRequest]) { request =>
              complete(upgradeDomain(namespace, domainId, request))
            }
          }
        }
      } ~ (get & pathPrefix("version")) {
        path("convergence") {
          authorize(isServerAdmin(authProfile)) {
            complete(onGetConvergenceVersion())
          }
        } ~ path("domain" / Segment / Segment) { (namespace, domainId) =>
          authorize(canAccessDomain(DomainId(namespace, domainId), authProfile)) {
            complete(onGetDomainVersion(namespace, domainId))
          }
        }
      }
    }
  }

  private[this] def upgradeConvergence(request: UpgradeRequest): Future[RestResponse] = {
    logger.debug(s"Received an request to upgrade convergence database to version")
    databaseManager.ask[UpgradeConvergenceResponse](UpgradeConvergenceRequest)
      .map(_.response.fold(
        _ => InternalServerError,
        _ => OkResponse
      ))
  }

  private[this] def upgradeDomain(namespace: String, domainId: String, request: UpgradeRequest): Future[RestResponse] = {
    logger.debug(s"Received an request to upgrade domain database to version: $domainId")
    databaseManager.ask[UpgradeDomainResponse](UpgradeDomainRequest(DomainId(namespace, domainId), _))
      .map(_.response.fold(
        _ => InternalServerError,
        _ => OkResponse
      ))
  }

  private[this] def upgradeDomains(): Future[RestResponse] = {
    logger.debug(s"Received an request to upgrade all domain databases")
    databaseManager.ask[UpgradeDomainsResponse](UpgradeDomainsRequest)
      .map(_.response.fold(
        _ => InternalServerError,
        _ => OkResponse
      ))
  }

  private[this] def onGetConvergenceVersion(): Future[RestResponse] = {
    databaseManager.ask[GetConvergenceVersionResponse](GetConvergenceVersionRequest)
      .map(_.version.fold(
        _ => InternalServerError,
        version => okResponse(VersionResponse(version))
      ))
  }

  private[this] def onGetDomainVersion(namespace: String, domainId: String): Future[RestResponse] = {
    databaseManager.ask[GetDomainVersionResponse](GetDomainVersionRequest(DomainId(namespace, domainId), _))
      .map(_.version.fold(
        _ => InternalServerError,
        version => okResponse(VersionResponse(version))
      ))
  }
}

private[rest] object DatabaseManagerRestService {

  case class UpgradeRequest()

  case class VersionResponse(databaseVersion: Option[String])

}
