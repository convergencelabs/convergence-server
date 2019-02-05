package com.convergencelabs.server.db

import scala.util.Failure
import scala.util.Success
import scala.util.Try

import com.convergencelabs.server.datastore.OrientDBUtil
import com.convergencelabs.server.datastore.convergence.DomainStore
import com.convergencelabs.server.datastore.convergence.schema.DomainClass
import com.convergencelabs.server.domain.DomainDatabase
import com.convergencelabs.server.domain.DomainFqn

class DomainDatabaseFactory(orientDbUrl: String, convergenceDbProvider: DatabaseProvider) {

  val domainStore = new DomainStore(convergenceDbProvider)

  def getDomainAdminDatabase(fqn: DomainFqn): Try[DatabaseProvider] = {
    for {
      domainInfo <- getDomainInfo(fqn)
      dbProvider <- Success(new SingleDatabaseProvider(orientDbUrl, domainInfo.database, domainInfo.adminUsername, domainInfo.adminPassword))
      _ <- dbProvider.connect()
    } yield(dbProvider)
  }

  def getDomainAdminDatabasePool(fqn: DomainFqn): Try[DatabaseProvider] = {
    for {
      domainInfo <- getDomainInfo(fqn)
      dbProvider <- Success(new PooledDatabaseProvider(orientDbUrl, domainInfo.database, domainInfo.adminUsername, domainInfo.adminPassword))
      _ <- dbProvider.connect()
    } yield(dbProvider)
  }

  def getDomainDatabase(fqn: DomainFqn): Try[DatabaseProvider] = {
    for {
      domainInfo <- getDomainInfo(fqn)
      dbProvider <- Success(new SingleDatabaseProvider(orientDbUrl, domainInfo.database, domainInfo.username, domainInfo.password))
      _ <- dbProvider.connect()
    } yield(dbProvider)
  }

  def getDomainDatabasePool(fqn: DomainFqn): Try[DatabaseProvider] = {
    for {
      domainInfo <- getDomainInfo(fqn)
      dbProvider <- Success(new PooledDatabaseProvider(orientDbUrl, domainInfo.database, domainInfo.username, domainInfo.password))
      _ <- dbProvider.connect()
    } yield(dbProvider)
  }

  def getDomains(): Try[List[DomainFqn]] = {
    convergenceDbProvider.withDatabase { db =>
      val query = "SELECT namespace, id FROM Domain"
      OrientDBUtil.query(db, query).map { oDocs =>
        oDocs.map { oDoc => DomainFqn(oDoc.getProperty(DomainClass.Fields.Namespace), oDoc.getProperty(DomainClass.Fields.Id)) }
      }
    }
  }

  private[this] def getDomainInfo(fqn: DomainFqn): Try[DomainDatabase] = {
    domainStore.getDomainDatabase(fqn) flatMap {
      _ match {
        case Some(domainInfo) =>
          Success(domainInfo)
        case None =>
          Failure(new IllegalArgumentException("Domain does not exist"))
      }
    }
  }
}