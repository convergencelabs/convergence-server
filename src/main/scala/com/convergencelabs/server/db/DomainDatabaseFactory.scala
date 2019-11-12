/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

package com.convergencelabs.server.db

import scala.util.Failure
import scala.util.Success
import scala.util.Try

import com.convergencelabs.server.datastore.OrientDBUtil
import com.convergencelabs.server.datastore.convergence.DomainStore
import com.convergencelabs.server.datastore.convergence.schema.DomainClass
import com.convergencelabs.server.domain.DomainDatabase
import com.convergencelabs.server.domain.DomainId

class DomainDatabaseFactory(orientDbUrl: String, convergenceDbProvider: DatabaseProvider) {

  val domainStore = new DomainStore(convergenceDbProvider)

  def getDomainAdminDatabase(fqn: DomainId): Try[DatabaseProvider] = {
    for {
      domainInfo <- getDomainInfo(fqn)
      dbProvider <- Success(new SingleDatabaseProvider(orientDbUrl, domainInfo.database, domainInfo.adminUsername, domainInfo.adminPassword))
      _ <- dbProvider.connect()
    } yield(dbProvider)
  }

  def getDomainAdminDatabasePool(fqn: DomainId): Try[DatabaseProvider] = {
    for {
      domainInfo <- getDomainInfo(fqn)
      dbProvider <- Success(new PooledDatabaseProvider(orientDbUrl, domainInfo.database, domainInfo.adminUsername, domainInfo.adminPassword))
      _ <- dbProvider.connect()
    } yield(dbProvider)
  }

  def getDomainDatabase(fqn: DomainId): Try[DatabaseProvider] = {
    for {
      domainInfo <- getDomainInfo(fqn)
      dbProvider <- Success(new SingleDatabaseProvider(orientDbUrl, domainInfo.database, domainInfo.username, domainInfo.password))
      _ <- dbProvider.connect()
    } yield(dbProvider)
  }

  def getDomainDatabasePool(fqn: DomainId): Try[DatabaseProvider] = {
    for {
      domainInfo <- getDomainInfo(fqn)
      dbProvider <- Success(new PooledDatabaseProvider(orientDbUrl, domainInfo.database, domainInfo.username, domainInfo.password))
      _ <- dbProvider.connect()
    } yield(dbProvider)
  }

  def getDomains(): Try[List[DomainId]] = {
    convergenceDbProvider.withDatabase { db =>
      val query = "SELECT namespace, id FROM Domain"
      OrientDBUtil.query(db, query).map { oDocs =>
        oDocs.map { oDoc => DomainId(oDoc.getProperty(DomainClass.Fields.Namespace), oDoc.getProperty(DomainClass.Fields.Id)) }
      }
    }
  }

  private[this] def getDomainInfo(fqn: DomainId): Try[DomainDatabase] = {
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