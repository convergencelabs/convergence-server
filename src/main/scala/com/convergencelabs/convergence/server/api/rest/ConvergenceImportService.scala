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

import akka.actor.typed.{ActorRef, ActorSystem}
import akka.http.scaladsl.server.Directive.{addByNameNullaryApply, addDirectiveApply}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import com.convergencelabs.convergence.server.db.data.ConvergenceImporterActor._
import com.convergencelabs.convergence.server.db.data.{ConvergenceScript, JsonFormats}
import com.convergencelabs.convergence.server.security.AuthorizationProfile
import de.heikoseeberger.akkahttpjson4s.Json4sSupport
import grizzled.slf4j.Logging
import org.json4s.Formats
import org.json4s.jackson.Serialization

import scala.concurrent.{ExecutionContext, Future}

private[rest] class ConvergenceImportService(private[this] val importerActor: ActorRef[Message],
                                             private[this] val system: ActorSystem[_],
                                             private[this] val executionContext: ExecutionContext,
                                             private[this] val defaultTimeout: Timeout)
  extends Json4sSupport with Logging {

  private[this] implicit val serialization: Serialization.type = Serialization
  private[this] implicit val formats: Formats = JsonFormats.format

  private[this] implicit val ec: ExecutionContext = executionContext
  private[this] implicit val t: Timeout = defaultTimeout
  private[this] implicit val s: ActorSystem[_] = system

  val route: AuthorizationProfile => Route = { authProfile: AuthorizationProfile =>
    pathPrefix("data") {
      (post & pathPrefix("import")) {
        path("convergence") {
          handleWith(importConvergence)
        }
      } ~ (get & pathPrefix("export")) {
        path("domain" / Segment / Segment) { (namespace, domainId) =>
          complete(exportDomain(namespace, domainId))
        } ~ path("convergence" / Segment) { username =>
          complete(exportUser(username))
        }
      }
    }
  }

  private[this] def importConvergence(script: ConvergenceScript): Future[RestResponse] = {
    logger.debug(s"Received a convergence import request")
//    (importerActor ? ConvergenceImport(script)).mapTo[Unit].map(_ => OkResponse)
    Future.successful(OkResponse)
  }

  private[this] def exportDomain(namespace: String, domainId: String): Future[RestResponse] = {
    logger.debug(s"Received a domain export request: $namespace/$domainId")
//    (importerActor ? DomainExport(DomainId(namespace, domainId))).mapTo[DomainExportResponse].map {
//      case DomainExportResponse(script) => okResponse(script)
//    }
    Future.successful(OkResponse)
  }

  private[this] def exportUser(username: String): Future[RestResponse] = {
    logger.debug(s"Received a convergence export request for user: $username")
//    (importerActor ? ConvergenceExport(Some(username))).mapTo[ConvergenceExportResponse].map {
//      case ConvergenceExportResponse(script) => okResponse(script)
//    }
    Future.successful(OkResponse)
  }
}
