package com.convergencelabs.server.datastore

import java.util.Set

import scala.collection.JavaConversions.asScalaSet
import scala.util.Try

import com.convergencelabs.server.domain.DomainFqn
import com.orientechnologies.orient.core.db.OPartitionedDatabasePool
import com.orientechnologies.orient.core.index.OCompositeKey

import DomainDatabaseFactory.DBDomainIdIndex
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx

object DomainDatabaseFactory {
  val DBNameIndex = "Domain.dbName"
  val DBDomainIdIndex = "Domain.namespace_domainId"
}

class DomainDatabaseFactory(url: String, convergenceDbProvider: DatabaseProvider) {

  val domainDatabaseStore = new DomainDatabaseStore(convergenceDbProvider)

  def getDomainAdminDatabase(fqn: DomainFqn): Try[Option[ODatabaseDocumentTx]] = {
    domainDatabaseStore.getDomainDatabase(fqn) map {
      _ map { domainInfo =>
        new ODatabaseDocumentTx(s"${url}/${domainInfo.database}").open(domainInfo.adminUsername, domainInfo.adminUsername)
      }
    }
  }

  def getDomainAdminDatabasePool(fqn: DomainFqn): Try[Option[OPartitionedDatabasePool]] = {
    domainDatabaseStore.getDomainDatabase(fqn) map {
      _ map { domainInfo =>
        new OPartitionedDatabasePool(s"${url}/${domainInfo.database}", domainInfo.adminUsername, domainInfo.adminUsername)
      }
    }
  }

  def getDomainDatabase(fqn: DomainFqn): Try[Option[ODatabaseDocumentTx]] = {
    domainDatabaseStore.getDomainDatabase(fqn) map {
      _ map { domainInfo =>
        new ODatabaseDocumentTx(s"${url}/${domainInfo.database}").open(domainInfo.username, domainInfo.password)
      }
    }
  }

  def getDomainDatabasePool(fqn: DomainFqn): Try[Option[OPartitionedDatabasePool]] = {
    domainDatabaseStore.getDomainDatabase(fqn) map {
      _ map { domainInfo =>
        new OPartitionedDatabasePool(s"${url}/${domainInfo.database}", domainInfo.username, domainInfo.password)
      }
    }
  }

  def getDomains(): Try[List[DomainFqn]] = {
    convergenceDbProvider.tryWithDatabase { db =>
      val index = db.getMetadata.getIndexManager.getIndex(DBDomainIdIndex)
      val keys: Set[OCompositeKey] = index.cursor().toKeys().asInstanceOf[Set[OCompositeKey]]
      keys.toList.map { key => DomainFqn(key.getKeys.get(0).toString(), key.getKeys.get(1).toString()) }
    }
  }
}