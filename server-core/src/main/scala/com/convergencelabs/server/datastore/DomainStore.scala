package com.convergencelabs.server.datastore

import java.util.{ List => JavaList }

import scala.collection.JavaConverters.asScalaBufferConverter
import scala.collection.JavaConverters.mapAsJavaMapConverter
import scala.util.Try

import com.convergencelabs.server.datastore.mapper.DomainMapper.DomainUserToODocument
import com.convergencelabs.server.datastore.mapper.DomainMapper.ODocumentToDomain
import com.convergencelabs.server.datastore.mapper.DomainMapper.domainConfigToODocument
import com.convergencelabs.server.domain.Domain
import com.convergencelabs.server.domain.DomainFqn
import com.orientechnologies.orient.core.db.OPartitionedDatabasePool
import com.orientechnologies.orient.core.record.impl.ODocument
import com.orientechnologies.orient.core.sql.OCommandSQL
import com.orientechnologies.orient.core.sql.query.OSQLSynchQuery

import grizzled.slf4j.Logging

class DomainStore private[datastore] (dbPool: OPartitionedDatabasePool)
    extends AbstractDatabasePersistence(dbPool)
    with Logging {

  private[this] val Id = "id"
  private[this] val Namespace = "namespace"
  private[this] val DomainId = "domainId"

  def createDomain(domain: Domain): Try[Unit] = tryWithDb { db =>
    db.save(domain.asODocument)
    Unit
  }

  def domainExists(domainFqn: DomainFqn): Try[Boolean] = tryWithDb { db =>
    val queryString =
      """SELECT id
        |FROM Domain
        |WHERE
        |  namespace = :namespace AND
        |  domainId = :domainId""".stripMargin

    val query = new OSQLSynchQuery[ODocument](queryString)
    val params = Map(Namespace -> domainFqn.namespace, DomainId -> domainFqn.domainId)
    val result: JavaList[ODocument] = db.command(query).execute(params.asJava)

    QueryUtil.enforceSingletonResultList(result) match {
      case Some(_) => true
      case None => false
    }
  }

  def getDomainByFqn(domainFqn: DomainFqn): Try[Option[Domain]] = tryWithDb { db =>
    val queryString = "SELECT FROM Domain WHERE namespace = :namespace AND domainId = :domainId"
    val query = new OSQLSynchQuery[ODocument](queryString)

    val params = Map(
      Namespace -> domainFqn.namespace,
      DomainId -> domainFqn.domainId)

    val result: JavaList[ODocument] = db.command(query).execute(params.asJava)

    QueryUtil.mapSingletonList(result) { doc => doc.asDomain }
  }

  def getDomainById(id: String): Try[Option[Domain]] = tryWithDb { db =>
    val query = new OSQLSynchQuery[ODocument]("SELECT * FROM Domain WHERE id = :id")
    val params = Map(Id -> id)
    val result: JavaList[ODocument] = db.command(query).execute(params.asJava)
    QueryUtil.mapSingletonList(result) { doc => doc.asDomain }
  }

  def getDomainsInNamespace(namespace: String): Try[List[Domain]] = tryWithDb { db =>
    val query = new OSQLSynchQuery[ODocument]("SELECT FROM Domain WHERE namespace = :namespace")
    val params = Map(Namespace -> namespace)
    val result: JavaList[ODocument] = db.command(query).execute(params.asJava)
    result.asScala.toList map { doc => doc.asDomain }
  }

  def removeDomain(id: String): Try[Boolean] = tryWithDb { db =>
    val command = new OCommandSQL("DELETE FROM Domain WHERE id = :id")
    val params = Map(Id -> id)
    val count: Int = db.command(command).execute(params.asJava)
    count > 0
  }

  def updateDomain(domain: Domain): Try[Unit] = tryWithDb { db =>
    val query = new OSQLSynchQuery[ODocument]("SELECT FROM Domain WHERE id = :id")
    val params = Map(Id -> domain.id)
    val result: JavaList[ODocument] = db.command(query).execute(params.asJava)

    QueryUtil.enforceSingletonResultList(result) match {
      case Some(doc) => {
        doc.merge(domain, false, false)
        db.save(doc)
        Unit
      }
      case None => throw new IllegalArgumentException(
          s"Domain to update could not be found: ${domain.domainFqn.namespace}/${domain.domainFqn.domainId}")
    }
  }
}
