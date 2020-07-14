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

import com.convergencelabs.convergence.server.backend.datastore.convergence.{ConvergenceSchemaVersionLogStore, DomainSchemaVersionLogStore}
import com.convergencelabs.convergence.server.backend.db.schema.SchemaManager.SchemaUpgradeError
import com.convergencelabs.convergence.server.backend.db.{DatabaseProvider, DomainDatabaseFactory}
import com.convergencelabs.convergence.server.model.DomainId
import grizzled.slf4j.Logging

import scala.util.Try

private[backend] final class DatabaseManager(databaseUrl: String,
                                             convergenceDbProvider: DatabaseProvider) extends Logging {

  private[this] val domainVersionStore = new DomainSchemaVersionLogStore(convergenceDbProvider)
  private[this] val convergenceVersionStore = new ConvergenceSchemaVersionLogStore(convergenceDbProvider)
  private[this] val domainProvider = new DomainDatabaseFactory(databaseUrl, convergenceDbProvider)

  def getConvergenceVersion(): Try[Option[String]] = {
    convergenceVersionStore.getConvergenceSchemaVersion()
  }

  def getDomainVersion(domainId: DomainId): Try[Option[String]] = {
    domainVersionStore.getDomainSchemaVersion(domainId)
  }

  def upgradeConvergence(): Either[SchemaUpgradeError, Unit] = {
    logger.debug("Upgrading the convergence database to the latest version")
    val schemaManager = new ConvergenceSchemaManager(convergenceDbProvider)
    schemaManager.upgrade()
  }

  def upgradeDomain(domainId: DomainId): Either[String, Unit] = {
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
