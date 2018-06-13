package com.convergencelabs.server.db

import scala.util.Failure
import scala.util.Success
import scala.util.Try
import com.convergencelabs.server.domain.DomainDatabase
import com.convergencelabs.server.domain.DomainFqn
import com.orientechnologies.orient.core.db.OPartitionedDatabasePool
import com.orientechnologies.orient.core.db.document.ODatabaseDocument
import com.orientechnologies.orient.core.metadata.schema.OType
import com.orientechnologies.orient.core.record.impl.ODocument
import com.convergencelabs.server.datastore.convergence.DomainDatabaseStore
import com.convergencelabs.server.datastore.convergence.DomainStore
import com.convergencelabs.server.datastore.OrientDBUtil

object DomainDatabaseFactory {
  val DBDomainIdIndex = "Domain.namespace_id"
}

class DomainDatabaseFactory(url: String, convergenceDbProvider: DatabaseProvider) {

  val orientDb = new Orie
  val domainDatabaseStore = new DomainDatabaseStore(convergenceDbProvider)

  def getDomainAdminDatabase(fqn: DomainFqn): Try[ODatabaseDocument] = {
    getDomainInfo(fqn) map {
      domainInfo =>

        def db = new ODatabaseDocument(s"${url}/${domainInfo.database}")
        db.open(domainInfo.adminUsername, domainInfo.adminPassword).asInstanceOf[ODatabaseDocument]
    }
  }

  def getDomainAdminDatabasePool(fqn: DomainFqn): Try[OPartitionedDatabasePool] = {
    getDomainInfo(fqn) map {
      domainInfo =>
        new OPartitionedDatabasePool(s"${url}/${domainInfo.database}", domainInfo.adminUsername, domainInfo.adminPassword)
    }
  }

  def getDomainDatabase(fqn: DomainFqn): Try[ODatabaseDocument] = {
    getDomainInfo(fqn) map {
      domainInfo =>
        new ODatabaseDocument(s"${url}/${domainInfo.database}").open(domainInfo.username, domainInfo.password)
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
      OrientDBUtil.query(db, query).map { oDocs =>
        oDocs.map { oDoc => DomainFqn(oDoc.field(DomainStore.Fields.Namespace, OType.STRING), oDoc.field(DomainStore.Fields.Id, OType.STRING)) }
      }.get
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