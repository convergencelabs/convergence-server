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

package com.convergencelabs.convergence.server.backend.db.schema

import com.convergencelabs.convergence.server.backend.datastore.convergence._
import com.convergencelabs.convergence.server.backend.db.schema.SchemaManager.SchemaUpgradeError
import com.convergencelabs.convergence.server.backend.db.{DatabaseProvider, DomainDatabaseFactory}
import com.convergencelabs.convergence.server.backend.services.domain.DomainPersistenceManagerActor.DomainNotFoundException
import com.convergencelabs.convergence.server.model.DomainId
import com.convergencelabs.convergence.server.model.server.domain.DomainStatus
import grizzled.slf4j.Logging

import scala.util.{Failure, Success, Try}

private[backend] final class DatabaseManager(databaseUrl: String,
                                             convergenceDbProvider: DatabaseProvider,
                                             convergenceSchemaVersion: SchemaVersion,
                                             domainSchemaVersion: SchemaVersion) extends Logging {

  private[this] val domainStore = new DomainStore(convergenceDbProvider)
  private[this] val domainVersionStore = new DomainSchemaVersionLogStore(convergenceDbProvider)
  private[this] val convergenceVersionStore = new ConvergenceSchemaVersionLogStore(convergenceDbProvider)
  private[this] val convergenceDeltaStore = new ConvergenceSchemaDeltaLogStore(convergenceDbProvider)
  private[this] val domainDeltaStore = new DomainSchemaDeltaLogStore(convergenceDbProvider)
  private[this] val domainProvider = new DomainDatabaseFactory(databaseUrl, convergenceDbProvider)

  def getConvergenceSchemaStatus(): Try[Option[DatabaseSchemaStatus]] = {
    for {
      version <- convergenceVersionStore.getConvergenceSchemaVersion()
      healthy <- convergenceDeltaStore.isConvergenceDBHealthy()
      error <- convergenceDeltaStore.getLastDeltaError()
    } yield {
      version.map(v =>
        DatabaseSchemaStatus(
          v, SchemaVersionUtil.computeSchemaVersionStatus(convergenceSchemaVersion, v), healthy, error)
      )
    }
  }

  def getConvergenceVersionLog(): Try[List[ConvergenceSchemaVersionLogEntry]] = {
    convergenceVersionStore.getConvergenceSchemaVersionLog()
  }

  def getConvergenceDeltaLog(): Try[List[ConvergenceSchemaDeltaLogEntry]] = {
    convergenceDeltaStore.appliedConvergenceDeltas()
  }

  def upgradeConvergence(): Either[SchemaUpgradeError, Unit] = {
    logger.debug("Upgrading the convergence database to the latest version")
    val schemaManager = new ConvergenceSchemaManager(convergenceDbProvider)
    schemaManager.upgrade()
  }

  def getDomainSchemaStatus(domainId: DomainId): Try[DatabaseSchemaStatus] = {
    for {
      version <- domainStore.findDomainDatabase(domainId).flatMap {
        case None =>
          Failure(DomainNotFoundException(domainId))
        case Some(d) =>
          Success(d)
      }
      healthy <- domainDeltaStore.isDomainDBHealthy(domainId)
      error <- domainDeltaStore.getLastDeltaErrorForDomain(domainId)
    } yield {
      DatabaseSchemaStatus(
        version.schemaVersion,
        SchemaVersionUtil.computeSchemaVersionStatus(domainSchemaVersion, version.schemaVersion),
        healthy,
        error)
    }
  }

  def getDomainVersionLog(domainId: DomainId): Try[List[DomainSchemaVersionLogEntry]] = {
    domainVersionStore.getDomainSchemaVersionLog(domainId)
  }

  def getDomainDeltaLog(domainId: DomainId): Try[List[DomainSchemaDeltaLogEntry]] = {
    domainDeltaStore.appliedDeltasForDomain(domainId)
  }

  def upgradeDomain(domainId: DomainId): Either[String, Unit] = {
    debug(s"Upgrading domain $domainId")
    for {
      db <- getDomainDatabaseProvider(domainId)
      schemaManger = new DomainSchemaManager(domainId, convergenceDbProvider, db)
      _ <- schemaManger.upgrade().left.map(_ => "Error upgrading domain schema")
    }
    yield ()
  }

  def upgradeAllDomains(): Try[Unit] = {
    domainProvider.getDomains() map { domainList =>
      domainList.foreach(upgradeDomain)
    }
  }

  private[this] def getDomainDatabaseProvider(domainId: DomainId): Either[String, DatabaseProvider] = {
    domainProvider
      .getDomainAdminDatabase(domainId)
      .map(Right(_))
      .recover { cause =>
        error("Error getting domain database", cause)
        Left(cause.getMessage)
      }
      .getOrElse(Left("unknown error getting domain database"))
  }
}
