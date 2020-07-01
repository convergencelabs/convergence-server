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
import com.convergencelabs.convergence.server.backend.db.schema.DatabaseManagerActor
import com.convergencelabs.convergence.server.backend.db.schema.DatabaseManagerActor._
import com.convergencelabs.convergence.server.model.DomainId
import com.convergencelabs.convergence.server.security.AuthorizationProfile
import grizzled.slf4j.Logging

import scala.concurrent.{ExecutionContext, Future}

private[rest] object DatabaseManagerRestService {

  case class UpgradeRequest(version: Option[Int], preRelease: Option[Boolean])

  case class VersionResponse(databaseVersion: Int)

}

private[rest] class DatabaseManagerRestService(executionContext: ExecutionContext,
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
            handleWith(upgradeDomains)
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
    val UpgradeRequest(version, preRelease) = request
    val to = toVersion(version, preRelease)
    logger.debug(s"Received an request to upgrade convergence database to version: $to")
    databaseManager.ask[UpgradeConvergenceResponse](UpgradeConvergenceRequest(version, preRelease.getOrElse(false), _))
      .map(_.response.fold(
        _ => InternalServerError,
        _ => OkResponse
      ))
  }

  private[this] def upgradeDomain(namespace: String, domainId: String, request: UpgradeRequest): Future[RestResponse] = {
    val UpgradeRequest(version, preRelease) = request
    val to = toVersion(version, preRelease)
    logger.debug(s"Received an request to upgrade domain database to version: $to")
    databaseManager.ask[UpgradeDomainResponse](UpgradeDomainRequest(DomainId(namespace, domainId), version, preRelease.getOrElse(false), _))
      .map(_.response.fold(
        _ => InternalServerError,
        _ => OkResponse
      ))
  }

  private[this] def upgradeDomains(request: UpgradeRequest): Future[RestResponse] = {
    val UpgradeRequest(version, preRelease) = request
    val to = toVersion(version, preRelease)
    logger.debug(s"Received an request to upgrade all domain databases to version: $to")
    databaseManager.ask[UpgradeDomainsResponse](UpgradeDomainsRequest(version, preRelease.getOrElse(false), _))
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

  private[this] def toVersion(version: Option[Int], preRelease: Option[Boolean]): String = {
    val v = version.map(_.toString) getOrElse "latest"
    val p = if (preRelease.getOrElse(false)) {
      "pre-released"
    } else {
      "released"
    }
    s"$v ($p)"
  }
}
