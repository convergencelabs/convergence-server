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

package com.convergencelabs.convergence.server.backend.db

import com.convergencelabs.convergence.server.backend.datastore.OrientDBUtil
import com.convergencelabs.convergence.server.backend.datastore.convergence.DomainStore
import com.convergencelabs.convergence.server.backend.datastore.convergence.schema.DomainClass
import com.convergencelabs.convergence.server.model.DomainId
import com.convergencelabs.convergence.server.model.server.domain.DomainDatabase

import scala.util.{Failure, Success, Try}

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
      dbProvider <- Success(new PooledDatabaseProvider(orientDbUrl, domainInfo.database, domainInfo.adminUsername, domainInfo.adminPassword, 0, 100))
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
      dbProvider <- Success(new PooledDatabaseProvider(orientDbUrl, domainInfo.database, domainInfo.username, domainInfo.password, 0, 100))
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