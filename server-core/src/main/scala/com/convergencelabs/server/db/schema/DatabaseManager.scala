package com.convergencelabs.server.db.schema

import scala.language.reflectiveCalls
import scala.util.Failure
import scala.util.Success
import scala.util.Try

import com.convergencelabs.server.datastore.DomainDBProvider
import com.convergencelabs.server.domain.DomainFqn
import com.orientechnologies.orient.core.db.OPartitionedDatabasePool

import DatabaseManager.DatabaseVersion
import grizzled.slf4j.Logging

object DatabaseManager {
  case class DatabaseVersion(managerVerion: Int, databaseVersion: Int)
}

class DatabaseManager(url: String, dbPool: OPartitionedDatabasePool) extends Logging {
  val versionController = new DatabaseVersionController(dbPool)
  val domainProvider = new DomainDBProvider(url, dbPool)

  def getConvergenceVersion(): Try[DatabaseVersion] = {
    for {
      managerVersion <- versionController.getManagerVersion()
      version <- versionController.getVersion()
    } yield {
      DatabaseVersion(managerVersion, version)
    }
  }

  def getDomainVersion(fqn: DomainFqn): Try[DatabaseVersion] = getDbPool(fqn) { dbPool =>
    val domainVersionController = new DatabaseVersionController(dbPool)
    for {
      managerVersion <- domainVersionController.getManagerVersion()
      version <- domainVersionController.getVersion()
    } yield {
      DatabaseVersion(managerVersion, version)
    }
  }

  def updagradeConvergence(version: Int, preRelease: Boolean): Try[Unit] = {
    val schemaManager = new DatabaseSchemaManager(dbPool, DeltaCategory.Convergence, preRelease)
    schemaManager.upgradeToVersion(version)
  }

  def updagradeConvergenceToLatest(preRelease: Boolean): Try[Unit] = {
    val schemaManager = new DatabaseSchemaManager(dbPool, DeltaCategory.Convergence, preRelease)
    schemaManager.upgradeToLatest()
  }

  def upgradeDomain(fqn: DomainFqn, version: Int, preRelease: Boolean): Try[Unit] = getDbPool(fqn) { dbPool =>
    val schemaManager = new DatabaseSchemaManager(dbPool, DeltaCategory.Domain, preRelease)
    schemaManager.upgradeToVersion(version)
  }

  def upgradeDomainToLatest(fqn: DomainFqn, preRelease: Boolean): Try[Unit] = getDbPool(fqn) { dbPool =>
    val schemaManager = new DatabaseSchemaManager(dbPool, DeltaCategory.Domain, preRelease)
    schemaManager.upgradeToLatest()
  }

  def upgradeAllDomains(version: Int, preRelease: Boolean): Try[Unit] = {
    domainProvider.getDomains() map {
      case domainList =>
        val dbPools = domainList.map { fqn => domainProvider.getDomainDBPool(fqn) }.flatMap { _.get }
        dbPools.foreach { dbPool =>
          val schemaManager = new DatabaseSchemaManager(dbPool, DeltaCategory.Domain, preRelease)
          schemaManager.upgradeToVersion(version)
        }
    }
  }

  def upgradeAllDomainsToLatest(preRelease: Boolean): Try[Unit] = {
    domainProvider.getDomains() map {
      case domainList =>
        val dbPools = domainList.map { fqn => domainProvider.getDomainDBPool(fqn) }.flatMap { _.get }
        dbPools.foreach { dbPool =>
          val schemaManager = new DatabaseSchemaManager(dbPool, DeltaCategory.Domain, preRelease)
          schemaManager.upgradeToLatest() match {
            case Success(()) =>
            case Failure(e) => logger.error("Upgrade Failed")
          }
        }
    }
  }

  private[this] def getDbPool[T](fqn: DomainFqn)(f: (OPartitionedDatabasePool) => Try[T]): Try[T] = {
    domainProvider.getDomainDBPool(fqn) flatMap {
      case Some(dbPool) =>
        f(dbPool)
      case None =>
        Failure(throw new IllegalArgumentException("Domain does not exist"))
    }
  }
}
