package com.convergencelabs.server.db.schema

import scala.language.reflectiveCalls
import scala.util.Failure
import scala.util.Try

import com.convergencelabs.server.datastore.DatabaseProvider
import com.convergencelabs.server.datastore.DeltaHistoryStore
import com.convergencelabs.server.datastore.DomainDatabaseFactory
import com.convergencelabs.server.domain.DomainFqn
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx

import grizzled.slf4j.Logging

class DatabaseManager(url: String, convergenceDbProvider: DatabaseProvider) extends Logging {

  private[this] val deltaHistoryStore = new DeltaHistoryStore(convergenceDbProvider)
  private[this] val domainProvider = new DomainDatabaseFactory(url, convergenceDbProvider)
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
        deltaHistoryStore.getConvergenceDBVersion() map { currentVersion =>
          convergenceDbProvider.tryWithDatabase { db =>
            currentVersion to version foreach { _ => upgradeConvergenceToNextVersion(db, preRelease).get }
          }
        }
      }
    }
  }

  def updagradeConvergenceToLatest(preRelease: Boolean): Try[Unit] = {
    convergenceDbProvider.withDatabase { db =>
      val schemaManager = new ConvergenceSchemaManager(db, deltaHistoryStore, preRelease)
      schemaManager.upgrade()
    }
  }

  private[this] def upgradeConvergenceToNextVersion(db: ODatabaseDocumentTx, preRelease: Boolean): Try[Unit] = {
    deltaHistoryStore.getConvergenceDBVersion() flatMap { version =>
      val schemaManager = new ConvergenceSchemaManager(db, deltaHistoryStore, preRelease)
      // TODO we could look up the max version first.
      schemaManager.upgrade(version + 1)
    }
  }

  def upgradeDomain(fqn: DomainFqn, version: Int, preRelease: Boolean): Try[Unit] = getDb(fqn) { db =>
    val schemaManger = new DomainSchemaManager(fqn, db, deltaHistoryStore, preRelease)
    schemaManger.upgrade()
  }

  def upgradeDomainToLatest(fqn: DomainFqn, preRelease: Boolean): Try[Unit] = getDb(fqn) { db =>
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

  private[this] def getDb[T](fqn: DomainFqn)(f: (ODatabaseDocumentTx) => Try[T]): Try[T] = {
    domainProvider.getDomainAdminDatabase(fqn) flatMap {
      case Some(db) =>
        f(db)
      case None =>
        Failure(throw new IllegalArgumentException("Domain does not exist"))
    }
  }
}
