package com.convergencelabs.server.datastore

import java.util.Set

import scala.collection.JavaConversions.asScalaSet
import scala.util.Try

import com.convergencelabs.server.domain.DomainFqn
import com.orientechnologies.orient.core.db.OPartitionedDatabasePool
import com.orientechnologies.orient.core.index.OCompositeKey

import DomainDBProvider.DBDomainIdIndex

object DomainDBProvider {
  val DBNameIndex = "Domain.dbName"
  val DBDomainIdIndex = "Domain.namespace_domainId"
}

class DomainDBProvider(url: String, dbPool: OPartitionedDatabasePool) {

  val domainDatabaseStore = new DomainDatabaseStore(dbPool)

  def getDomainAdminDBPool(fqn: DomainFqn): Try[Option[OPartitionedDatabasePool]] = {
    domainDatabaseStore.getDomainDatabase(fqn) map {
      _ map { domainInfo =>
        new OPartitionedDatabasePool(s"${url}/${domainInfo.database}", domainInfo.adminUsername, domainInfo.adminUsername)
      }
    }
  }
  
  def getDomainDBPool(fqn: DomainFqn): Try[Option[OPartitionedDatabasePool]] = {
    domainDatabaseStore.getDomainDatabase(fqn) map {
      _ map { domainInfo =>
        new OPartitionedDatabasePool(s"${url}/${domainInfo.database}", domainInfo.username, domainInfo.password)
      }
    }
  }

  def getDomains(): Try[List[DomainFqn]] = Try {
    val db = dbPool.acquire()
    val index = db.getMetadata.getIndexManager.getIndex(DBDomainIdIndex)
    val keys: Set[OCompositeKey] = index.cursor().toKeys().asInstanceOf[Set[OCompositeKey]]
    keys.toList.map { key => DomainFqn(key.getKeys.get(0).toString(), key.getKeys.get(1).toString()) }
  }
}