package com.convergencelabs.server.db.schema

import scala.language.reflectiveCalls
import scala.util.Failure
import scala.util.Success
import scala.util.Try

import com.convergencelabs.server.datastore.DomainDBProvider
import com.convergencelabs.server.domain.DomainFqn
import com.orientechnologies.orient.core.db.OPartitionedDatabasePool

import grizzled.slf4j.Logging
import com.convergencelabs.server.datastore.DeltaHistoryStore
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx

object DatabaseManager {
}

class DatabaseManager(url: String, dbPool: OPartitionedDatabasePool) extends Logging {

  private[this] val deltaHistoryStore = new DeltaHistoryStore(dbPool)
  private[this] val domainProvider = new DomainDBProvider(url, dbPool)
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
          val db = dbPool.acquire()
          try {
            currentVersion to version foreach { _ => upgradeConvergenceToNextVersion(db, preRelease) }
          } finally {
            db.close()
          }
        }
      }
    }
  }

  def updagradeConvergenceToLatest(preRelease: Boolean): Try[Unit] = {
    deltaManager.manifest(DeltaCategory.Convergence) map { manifest =>
      deltaHistoryStore.getConvergenceDBVersion() map { currentVersion =>
        val version = if (preRelease) {
          manifest.maxPreReleaseVersion()
        } else {
          manifest.maxPreReleaseVersion()
        }
        val db = dbPool.acquire()
        try {
          currentVersion to version foreach { _ => upgradeConvergenceToNextVersion(db, preRelease) }
        } finally {
          db.close()
        }
      }
    }
  }

  private[this] def upgradeConvergenceToNextVersion(db: ODatabaseDocumentTx, preRelease: Boolean): Try[Unit] = {
    deltaHistoryStore.getConvergenceDBVersion() map { version =>
      DatabaseSchemaManager.upgradeToNextVersion(db, DeltaCategory.Domain, version, preRelease) match {
        case Success(delta) => //deltaHistoryStore.saveConvergenceDeltaHistory(deltaHistory)
      }
    }
  }

  def upgradeDomain(fqn: DomainFqn, version: Int, preRelease: Boolean): Try[Unit] = getDb(fqn) { db =>
    deltaManager.manifest(DeltaCategory.Domain) map { manifest =>
      if ((preRelease && version > manifest.maxPreReleaseVersion()) ||
        (!preRelease && version > manifest.maxReleasedVersion())) {
        Failure(new IllegalArgumentException("version is greater than max version"))
      } else {
        deltaHistoryStore.getDomainDBVersion(fqn) map { currentVersion =>
          currentVersion to version foreach { _ => upgradeDomainToNextVersion(db, fqn, preRelease) }
        }
      }
    }
  }

  def upgradeDomainToLatest(fqn: DomainFqn, preRelease: Boolean): Try[Unit] = getDb(fqn) { db =>
    deltaManager.manifest(DeltaCategory.Domain) map { manifest =>
      deltaHistoryStore.getDomainDBVersion(fqn) map { currentVersion =>
        val version = if (preRelease) {
          manifest.maxPreReleaseVersion()
        } else {
          manifest.maxPreReleaseVersion()
        }
        currentVersion to version foreach { _ => upgradeDomainToNextVersion(db, fqn, preRelease) }
      }
    }
  }

  def upgradeAllDomains(version: Int, preRelease: Boolean): Try[Unit] = {
    domainProvider.getDomains() map {
      case domainList => domainList.foreach { upgradeDomain(_, version, preRelease) }
    }
  }

  def upgradeAllDomainsToLatest(preRelease: Boolean): Try[Unit] = {
    domainProvider.getDomains() map {
      case domainList => domainList.foreach { upgradeDomainToLatest(_, preRelease) }
    }
  }

  def upgradeDomainToNextVersion(db: ODatabaseDocumentTx, fqn: DomainFqn, preRelease: Boolean): Try[Unit] = {
    deltaHistoryStore.getDomainDBVersion(fqn) map { version =>
      DatabaseSchemaManager.upgradeToNextVersion(db, DeltaCategory.Domain, version)
    }
  }

  private[this] def getDb[T](fqn: DomainFqn)(f: (ODatabaseDocumentTx) => Try[T]): Try[T] = {
    domainProvider.getDomainDB(fqn) flatMap {
      case Some(db) =>
        f(db)
      case None =>
        Failure(throw new IllegalArgumentException("Domain does not exist"))
    }
  }
}
