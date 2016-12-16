package com.convergencelabs.server.db.schema

import scala.util.Failure
import scala.util.Try

import com.convergencelabs.server.datastore.DatabaseProvider
import com.convergencelabs.server.datastore.DeltaHistoryStore
import com.convergencelabs.server.datastore.DomainDatabaseFactory
import com.convergencelabs.server.domain.DomainFqn
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx

import grizzled.slf4j.Logging
import com.typesafe.config.Config

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

  def upgradeAllDomains(version: Int, preRelease: Boolean): Try[Unit] = {
    domainProvider.getDomains() map { domainList =>
      domainList.foreach { upgradeDomain(_, version, preRelease) }
    }
  }

  def upgradeAllDomainsToLatest(preRelease: Boolean): Try[Unit] = {
    domainProvider.getDomains() map { domainList =>
      domainList.foreach { upgradeDomainToLatest(_, preRelease) }
    }
  }

  def upgradeDomainToNextVersion(db: ODatabaseDocumentTx, fqn: DomainFqn, preRelease: Boolean): Try[Unit] = {
    deltaHistoryStore.getDomainDBVersion(fqn) flatMap { version =>
      val schemaManger = new DomainSchemaManager(fqn, db, deltaHistoryStore, preRelease)
      schemaManger.upgrade(version + 1)
    }
  }

  private[this] def withDomainDatabase[T](fqn: DomainFqn)(f: (ODatabaseDocumentTx) => Try[T]): Try[T] = {
    domainProvider.getDomainAdminDatabase(fqn) flatMap {
      case Some(db) =>
        val result = f(db)
        db.close()
        result
      case None =>
        Failure(throw new IllegalArgumentException("Domain does not exist"))
    }
  }

  private[this] def withConvergenceDatabase[T](f: (ODatabaseDocumentTx) => Try[T]): Try[T] = {
    val username = dbConfig.getString("admin-username")
    val password = dbConfig.getString("admin-password")
    val database = dbConfig.getString("database")

    val convergenceUrl = s"${databaseUrl}/${database}"
    val db = new ODatabaseDocumentTx(convergenceUrl)
    db.open(username, password)

    db.activateOnCurrentThread()
    val result = f(db)

    db.activateOnCurrentThread()
    db.close()
    result
  }
}
