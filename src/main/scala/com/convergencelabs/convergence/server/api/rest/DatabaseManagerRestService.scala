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
import com.convergencelabs.convergence.server.api.rest.DatabaseManagerRestService._
import com.convergencelabs.convergence.server.backend.datastore.convergence.{ConvergenceSchemaDeltaLogEntry, DomainSchemaDeltaLogEntry}
import com.convergencelabs.convergence.server.backend.db.schema.DatabaseSchemaStatus
import com.convergencelabs.convergence.server.backend.services.server.DatabaseManagerActor
import com.convergencelabs.convergence.server.backend.services.server.DatabaseManagerActor._
import com.convergencelabs.convergence.server.model.DomainId
import com.convergencelabs.convergence.server.security.AuthorizationProfile
import grizzled.slf4j.Logging

import java.time.Instant
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
      pathPrefix("convergence") {
        get {
          pathEnd {
            complete(onGetConvergenceSchemaStatus())
          } ~ path("versions") {
            authorize(isServerAdmin(authProfile)) {
              complete(onGetConvergenceSchemaVersionLog())
            }
          } ~ path("deltas") {
            authorize(isServerAdmin(authProfile)) {
              complete(onGetConvergenceSchemaDeltaLog())
            }
          }
        } ~ post {
          path("upgrade") {
            authorize(isServerAdmin(authProfile)) {
              complete(onUpgradeConvergenceSchema())
            }
          }
        }
      } ~ pathPrefix("domains" / Segment / Segment) { (namespace, domainId) =>
        get {
          authorize(canAccessDomain(DomainId(namespace, domainId), authProfile)) {
            pathEnd {
              complete(onGetDomainSchemaStatus(namespace, domainId))
            } ~ path("versions") {
              complete(onGetDomainSchemaVersionLog(namespace, domainId))
            } ~ path("deltas") {
              complete(onGetDomainSchemaDeltaLog(namespace, domainId))
            }
          }
        } ~ post {
          path("upgrade") {
            authorize(canManageDomain(DomainId(namespace, domainId), authProfile)) {
              complete(onUpgradeDomainSchema(namespace, domainId))
            }
          }
        }
      }
    }
  }

  private[this] def onGetConvergenceSchemaStatus(): Future[RestResponse] = {
    databaseManager.ask[GetConvergenceSchemaStatusResponse](GetConvergenceSchemaStatusRequest)
      .map(_.status.fold(
        {
          case ConvergenceSchemaNotInstalledError() =>
            okResponse(DatabaseStatusResponse(None, Some("not_initialized"), Some("The convergence schema is not initialized")))
          case UnknownError() =>
            InternalServerError
        },
        status => createStatusResponse(status)
      ))
  }

  private[this] def onGetConvergenceSchemaVersionLog(): Future[RestResponse] = {
    databaseManager.ask[GetConvergenceVersionLogResponse](GetConvergenceVersionLogRequest)
      .map(_.versions.fold(
        _ => InternalServerError,
        versions =>
          okResponse(DatabaseVersionLogResponse(versions.map(v => VersionLogEntry(v.version, v.date))))
      ))
  }

  private[this] def onGetConvergenceSchemaDeltaLog(): Future[RestResponse] = {
    databaseManager.ask[GetConvergenceDeltaLogResponse](GetConvergenceDeltaLogRequest)
      .map(_.deltas.fold(
        _ => InternalServerError,
        { deltas =>
          val mapped = deltas.map {
            case ConvergenceSchemaDeltaLogEntry(sequenceNumber, id, tag, script, status, message, version, date) =>
              DeltaLogEntry(sequenceNumber, id, tag, date, version, status, message)
          }
          okResponse(DatabaseDeltaLogResponse(mapped))
        }
      ))
  }

  private[this] def onUpgradeConvergenceSchema(): Future[RestResponse] = {
    logger.debug("Received an request to upgrade convergence database")
    databaseManager.ask[UpgradeConvergenceResponse](UpgradeConvergenceRequest)
      .map(_.response.fold(
        _ => InternalServerError,
        _ => OkResponse
      ))
  }

  private[this] def onGetDomainSchemaStatus(namespace: String, domainId: String): Future[RestResponse] = {
    databaseManager.ask[GetDomainSchemaStatusResponse](GetDomainSchemaStatusRequest(DomainId(namespace, domainId), _))
      .map(_.status.fold(
        {
          case DomainNotFoundError() =>
            notFoundResponse("the requested domain does not exist")
          case UnknownError() =>
            InternalServerError
        },
        status => createStatusResponse(status)
      ))
  }

  private def createStatusResponse(status: DatabaseSchemaStatus): RestResponse = {
      val s = if (status.healthy) "healthy" else "error"
      okResponse(DatabaseStatusResponse(Some(status.version), Some(s), status.message))
  }

  private[this] def onUpgradeDomainSchema(namespace: String, domainId: String): Future[RestResponse] = {
    logger.debug(s"Received an request to upgrade domain database: $domainId")
    databaseManager.ask[UpgradeDomainResponse](UpgradeDomainRequest(DomainId(namespace, domainId), _))
      .map(_.response.fold(
        _ => InternalServerError,
        _ => OkResponse
      ))
  }

  private[this] def onGetDomainSchemaVersionLog(namespace: String, id: String): Future[RestResponse] = {
    val domainId = DomainId(namespace, id)
    databaseManager.ask[GetDomainVersionLogResponse](r => GetDomainVersionLogRequest(domainId, r))
      .map(_.versions.fold(
        _ => InternalServerError,
        versions =>
          okResponse(DatabaseVersionLogResponse(versions.map(v => VersionLogEntry(v.version, v.date))))
      ))
  }

  private[this] def onGetDomainSchemaDeltaLog(namespace: String, id: String): Future[RestResponse] = {
    val domainId = DomainId(namespace, id)
    databaseManager.ask[GetDomainDeltaLogResponse](r => GetDomainDeltaLogRequest(domainId, r))
      .map(_.deltas.fold(
        _ => InternalServerError,
        { deltas =>
          val mapped = deltas.map {
            case DomainSchemaDeltaLogEntry(_, sequenceNumber, id, tag, script, status, message, version, date) =>
              DeltaLogEntry(sequenceNumber, id, tag, date, version, status, message)
          }
          okResponse(DatabaseDeltaLogResponse(mapped))
        }
      ))
  }
}

private[rest] object DatabaseManagerRestService {

  final case class DatabaseStatusResponse(version: Option[String],
                                          status: Option[String],
                                          message: Option[String])

  final case class DatabaseVersionLogResponse(versions: List[VersionLogEntry])

  final case class VersionLogEntry(version: String, date: Instant)

  final case class DatabaseDeltaLogResponse(deltas: List[DeltaLogEntry])

  final case class DeltaLogEntry(sequenceNumber: Long,
                                 id: String,
                                 tag: Option[String],
                                 date: Instant,
                                 appliedForVersion: String,
                                 status: String,
                                 message: Option[String])

}
