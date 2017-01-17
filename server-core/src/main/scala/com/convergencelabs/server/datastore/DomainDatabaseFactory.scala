package com.convergencelabs.server.datastore

import java.util.Set
import scala.util.Try
import com.convergencelabs.server.domain.DomainFqn
import com.orientechnologies.orient.core.db.OPartitionedDatabasePool
import com.orientechnologies.orient.core.index.OCompositeKey
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx
import DomainDatabaseFactory._
import scala.collection.JavaConversions._
import com.orientechnologies.orient.core.index.OIndexCursor
import scala.collection.mutable.MutableList
import com.orientechnologies.orient.core.record.impl.ODocument
import com.orientechnologies.orient.core.metadata.schema.OType
import scala.util.Success
import scala.util.Failure
import com.convergencelabs.server.frontend.rest.DomainInfo
import com.convergencelabs.server.domain.DomainDatabase

object DomainDatabaseFactory {
  val DBDomainIdIndex = "Domain.namespace_id"
}

class DomainDatabaseFactory(url: String, convergenceDbProvider: DatabaseProvider) {

  val domainDatabaseStore = new DomainDatabaseStore(convergenceDbProvider)

  def getDomainAdminDatabase(fqn: DomainFqn): Try[ODatabaseDocumentTx] = {
    getDomainInfo(fqn) map {
      domainInfo =>
        def db = new ODatabaseDocumentTx(s"${url}/${domainInfo.database}")
        db.open(domainInfo.adminUsername, domainInfo.adminUsername).asInstanceOf[ODatabaseDocumentTx]
    }
  }

  def getDomainAdminDatabasePool(fqn: DomainFqn): Try[OPartitionedDatabasePool] = {
    getDomainInfo(fqn) map {
      domainInfo =>
        new OPartitionedDatabasePool(s"${url}/${domainInfo.database}", domainInfo.adminUsername, domainInfo.adminUsername)
    }
  }

  def getDomainDatabase(fqn: DomainFqn): Try[ODatabaseDocumentTx] = {
    getDomainInfo(fqn) map {
      domainInfo =>
        new ODatabaseDocumentTx(s"${url}/${domainInfo.database}").open(domainInfo.username, domainInfo.password)
    }
  }

  def getDomainDatabasePool(fqn: DomainFqn): Try[OPartitionedDatabasePool] = {
    getDomainInfo(fqn) map {
      domainInfo =>
        new OPartitionedDatabasePool(s"${url}/${domainInfo.database}", domainInfo.username, domainInfo.password)
    }
  }

  def getDomains(): Try[List[DomainFqn]] = {
    convergenceDbProvider.tryWithDatabase { db =>
      val query = "SELECT namespace, id FROM Domain"
      val oDocs: List[ODocument] = QueryUtil.query(query, Map(), db)
      oDocs.map { oDoc => DomainFqn(oDoc.field(DomainStore.Fields.Namespace, OType.STRING), oDoc.field(DomainStore.Fields.Id, OType.STRING)) }
    }
  }

  private[this] def getDomainInfo(fqn: DomainFqn): Try[DomainDatabase] = {
    domainDatabaseStore.getDomainDatabase(fqn) flatMap {
      _ match {
        case Some(domainInfo) =>
          Success(domainInfo)
        case None =>
          Failure(new IllegalArgumentException("Domain does not exist"))
      }
    }
  }
}