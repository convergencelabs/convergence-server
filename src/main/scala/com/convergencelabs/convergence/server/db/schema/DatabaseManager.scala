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

package com.convergencelabs.convergence.server.db.schema

import com.convergencelabs.convergence.server.datastore.convergence.DeltaHistoryStore
import com.convergencelabs.convergence.server.db.{DatabaseProvider, DomainDatabaseFactory}
import com.convergencelabs.convergence.server.domain.DomainId
import com.orientechnologies.orient.core.db.{OrientDB, OrientDBConfig}
import com.orientechnologies.orient.core.db.document.ODatabaseDocument
import com.typesafe.config.Config
import grizzled.slf4j.Logging

import scala.util.{Failure, Try}

class DatabaseManager(
  databaseUrl: String,
  convergenceDbProvider: DatabaseProvider,
  dbConfig: Config) extends Logging {

  private[this] val deltaHistoryStore = new DeltaHistoryStore(convergenceDbProvider)
  private[this] val domainProvider = new DomainDatabaseFactory(databaseUrl, convergenceDbProvider)
  private[this] val deltaManager = new DeltaManager(None)

  def getConvergenceVersion(): Try[Int] = {
    deltaHistoryStore.getConvergenceDBVersion()
  }

  def getDomainVersion(fqn: DomainId): Try[Int] = {
    deltaHistoryStore.getDomainDBVersion(fqn)
  }

  def updagradeConvergence(version: Int, preRelease: Boolean): Try[Unit] = {
    deltaManager.manifest(DeltaCategory.Convergence) map { manifest =>
      if ((preRelease && version > manifest.maxPreReleaseVersion()) ||
        (!preRelease && version > manifest.maxReleasedVersion())) {
        Failure(new IllegalArgumentException("version is greater than max version"))
      } else {
        withConvergenceDatabase { db =>
          val schemaManager = new ConvergenceSchemaManager(db, deltaHistoryStore, preRelease)
          schemaManager.upgrade(version)
        }
      }
    }
  }

  def updagradeConvergenceToLatest(preRelease: Boolean): Try[Unit] = withConvergenceDatabase { db =>
    logger.debug("Upgrading the convergence database to the latest version")
    val schemaManager = new ConvergenceSchemaManager(db, deltaHistoryStore, preRelease)
    schemaManager.upgrade()
  }

  def upgradeDomain(fqn: DomainId, version: Int, preRelease: Boolean): Try[Unit] = withDomainDatabase(fqn) { db =>
    val schemaManger = new DomainSchemaManager(fqn, db, deltaHistoryStore, preRelease)
    schemaManger.upgrade(version)
  }

  def upgradeDomainToLatest(fqn: DomainId, preRelease: Boolean): Try[Unit] = withDomainDatabase(fqn) { db =>
    val schemaManger = new DomainSchemaManager(fqn, db, deltaHistoryStore, preRelease)
    schemaManger.upgrade()
  }

  def upgradeAllDomains(version: Int, preRelease: Boolean): Unit = {
    domainProvider.getDomains() map { domainList =>
      domainList.foreach {
        domainFqn =>
          upgradeDomain(domainFqn, version, preRelease) recover {
            case e: Exception =>
              logger.error("Unable to upgrade domain", e)
          }
      }
    } recover {
      case e: Exception => {
        logger.error("Unable to lookup domains", e)
      }
    }
  }

  def upgradeAllDomainsToLatest(preRelease: Boolean): Try[Unit] = {
    domainProvider.getDomains() map { domainList =>
      domainList.foreach { upgradeDomainToLatest(_, preRelease) }
    }
  }

  def upgradeDomainToNextVersion(db: ODatabaseDocument, fqn: DomainId, preRelease: Boolean): Try[Unit] = {
    deltaHistoryStore.getDomainDBVersion(fqn) flatMap { version =>
      val schemaManger = new DomainSchemaManager(fqn, db, deltaHistoryStore, preRelease)
      schemaManger.upgrade(version + 1)
    }
  }

  private[this] def withDomainDatabase[T](fqn: DomainId)(f: (ODatabaseDocument) => Try[T]): Try[T] = {
    domainProvider.getDomainAdminDatabase(fqn) flatMap { dbProvider =>
      val result = dbProvider.withDatabase(f(_))
      dbProvider.shutdown()
      result
    }
  }

  private[this] def withConvergenceDatabase[T](f: (ODatabaseDocument) => Try[T]): Try[T] = {
    val username = dbConfig.getString("admin-username")
    val password = dbConfig.getString("admin-password")
    val database = dbConfig.getString("database")

    val orientDb = new OrientDB(databaseUrl, OrientDBConfig.defaultConfig())
    val db = orientDb.open(database, username, password)

    db.activateOnCurrentThread()
    val result = f(db)

    db.activateOnCurrentThread()
    db.close()
    result
  }
}
