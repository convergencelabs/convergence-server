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
import akka.http.scaladsl.server.Directives.{Segment, _enhanceRouteWithConcatenation, _segmentStringToPathMatcher, as, complete, entity, get, handleWith, path, pathPrefix, post}
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import akka.util.Timeout
import com.convergencelabs.convergence.server.api.rest.DatabaseManagerRestService.{UpgradeRequest, VersionResponse}
import com.convergencelabs.convergence.server.db.data.JsonFormats
import com.convergencelabs.convergence.server.db.schema.DatabaseManagerActor._
import com.convergencelabs.convergence.server.domain.DomainId
import com.convergencelabs.convergence.server.security.AuthorizationProfile
import de.heikoseeberger.akkahttpjson4s.Json4sSupport
import grizzled.slf4j.Logging
import org.json4s.Formats
import org.json4s.jackson.Serialization

import scala.concurrent.{ExecutionContext, Future}

object DatabaseManagerRestService {
  case class UpgradeRequest(version: Option[Int], preRelease: Option[Boolean])
  case class VersionResponse(databaseVersion: Int)
}

class DatabaseManagerRestService(
  private[this] val executionContext: ExecutionContext,
  private[this] val databaseManager: ActorRef,
  private[this] val defaultTimeout: Timeout)
    extends Json4sSupport
    with Logging {

  implicit val serialization: Serialization.type = Serialization
  implicit val formats: Formats = JsonFormats.format

  implicit val ec: ExecutionContext = executionContext
  implicit val t: Timeout = defaultTimeout

  val route: AuthorizationProfile => Route = { authProfile: AuthorizationProfile =>
    pathPrefix("schema") {
      (post & pathPrefix("upgrade")) {
        path("convergence") {
          handleWith(upgradeConvergence)
        } ~ path("domains") {
          handleWith(upgradeDomains)
        } ~ path("domain" / Segment / Segment) { (namespace, domainId) =>
          entity(as[UpgradeRequest]) { request =>
            complete(upgradeDomain(namespace, domainId, request))
          }
        }
      } ~ (get & pathPrefix("version")) {
        path("convergence") {
          complete(getConvergenceVersion())
        } ~ path("domain" / Segment / Segment) { (namespace, domainId) =>
          complete(getDomainVersion(namespace, domainId))
        }
      }
    }
  }

  def upgradeConvergence(request: UpgradeRequest): Future[RestResponse] = {
    val UpgradeRequest(version, preRelease) = request
    val to = toVersion(version, preRelease)
    logger.debug(s"Received an request to upgrade convergence database to version: $to")
    (databaseManager ? UpgradeConvergence(version, preRelease.getOrElse(false))).mapTo[Unit].map(_ => OkResponse)
  }

  def upgradeDomain(namespace: String, domainId: String, request: UpgradeRequest): Future[RestResponse] = {
    val UpgradeRequest(version, preRelease) = request
    val to = toVersion(version, preRelease)
    logger.debug(s"Received an request to upgrade domain database to version: $to")
    val message = UpgradeDomain(DomainId(namespace, domainId), version, preRelease.getOrElse(false))
    (databaseManager ? message).mapTo[Unit].map(_ => OkResponse)
  }

  def upgradeDomains(request: UpgradeRequest): Future[RestResponse] = {
    val UpgradeRequest(version, preRelease) = request
    val to = toVersion(version, preRelease)
    logger.debug(s"Received an request to upgrade all domain databases to version: $to")
    val message = UpgradeDomains(version, preRelease.getOrElse(false))
    (databaseManager ? message).mapTo[Unit].map(_ => OkResponse)
  }

  def getConvergenceVersion(): Future[RestResponse] = {
    val message = GetConvergenceVersion
    (databaseManager ? message).mapTo[Int].map { version =>
      okResponse(VersionResponse(version))
    }
  }

  def getDomainVersion(namespace: String, domainId: String): Future[RestResponse] = {
    val message = GetDomainVersion(DomainId(namespace, domainId))
    (databaseManager ? message).mapTo[Int].map { version =>
      okResponse(VersionResponse(version))
    }
  }

  private[this] def toVersion(version: Option[Int], preRelease: Option[Boolean]): String = {
    val v = version.map(_.toString) getOrElse ("latest")
    val p = if (preRelease.getOrElse(false)) {
      "pre-released"
    } else {
      "released"
    }
    s"$v ($p)"
  }
}
