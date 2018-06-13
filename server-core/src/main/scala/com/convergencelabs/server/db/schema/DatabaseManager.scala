package com.convergencelabs.server.db.schema

import scala.util.Failure
import scala.util.Try

import com.convergencelabs.server.db.DatabaseProvider
import com.convergencelabs.server.datastore.convergence.DeltaHistoryStore
import com.convergencelabs.server.db.DomainDatabaseFactory
import com.convergencelabs.server.domain.DomainFqn
import com.orientechnologies.orient.core.db.document.ODatabaseDocument

import grizzled.slf4j.Logging
import com.typesafe.config.Config
import com.orientechnologies.orient.core.db.OrientDBConfig
import com.orientechnologies.orient.core.db.OrientDB

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

  def getDomainVersion(fqn: DomainFqn): Try[Int] = {
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

  def upgradeDomain(fqn: DomainFqn, version: Int, preRelease: Boolean): Try[Unit] = withDomainDatabase(fqn) { db =>
    val schemaManger = new DomainSchemaManager(fqn, db, deltaHistoryStore, preRelease)
    schemaManger.upgrade(version)
  }

  def upgradeDomainToLatest(fqn: DomainFqn, preRelease: Boolean): Try[Unit] = withDomainDatabase(fqn) { db =>
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

  def upgradeDomainToNextVersion(db: ODatabaseDocument, fqn: DomainFqn, preRelease: Boolean): Try[Unit] = {
    deltaHistoryStore.getDomainDBVersion(fqn) flatMap { version =>
      val schemaManger = new DomainSchemaManager(fqn, db, deltaHistoryStore, preRelease)
      schemaManger.upgrade(version + 1)
    }
  }

  private[this] def withDomainDatabase[T](fqn: DomainFqn)(f: (ODatabaseDocument) => Try[T]): Try[T] = {
    domainProvider.getDomainAdminDatabase(fqn) flatMap { db =>
      val result = f(db)
      db.activateOnCurrentThread()
      db.close()
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
