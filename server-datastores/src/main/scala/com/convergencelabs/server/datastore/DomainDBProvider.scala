package com.convergencelabs.server.datastore

import java.util.Set

import scala.collection.JavaConversions.asScalaSet
import scala.util.Try

import com.convergencelabs.server.domain.DomainFqn
import com.orientechnologies.orient.core.db.OPartitionedDatabasePool
import com.orientechnologies.orient.core.index.OCompositeKey

import DomainDBProvider.DBDomainIdIndex
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx

object DomainDBProvider {
  val DBNameIndex = "Domain.dbName"
  val DBDomainIdIndex = "Domain.namespace_domainId"
}

class DomainDBProvider(url: String, dbPool: OPartitionedDatabasePool) {

  val domainDatabaseStore = new DomainDatabaseStore(dbPool)

  def getDomainAdminDB(fqn: DomainFqn): Try[Option[ODatabaseDocumentTx]] = {
    domainDatabaseStore.getDomainDatabase(fqn) map {
      _ map { domainInfo =>
        new ODatabaseDocumentTx(s"${url}/${domainInfo.database}").open(domainInfo.adminUsername, domainInfo.adminUsername)
      }
    }
  }
  
  def getDomainDB(fqn: DomainFqn): Try[Option[ODatabaseDocumentTx]] = {
    domainDatabaseStore.getDomainDatabase(fqn) map {
      _ map { domainInfo =>
        new ODatabaseDocumentTx(s"${url}/${domainInfo.database}").open(domainInfo.username, domainInfo.password)
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