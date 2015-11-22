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
import com.convergencelabs.server.util.TryWithResource
import com.orientechnologies.orient.core.db.OPartitionedDatabasePool
import com.orientechnologies.orient.core.record.impl.ODocument
import com.orientechnologies.orient.core.sql.OCommandSQL
import com.orientechnologies.orient.core.sql.query.OSQLSynchQuery

import grizzled.slf4j.Logging

class DomainStore(dbPool: OPartitionedDatabasePool) extends Logging {

  def createDomain(domain: Domain): Try[Unit] = TryWithResource(dbPool.acquire()) { db =>
    db.save(domain.asODocument)
  }

  def domainExists(domainFqn: DomainFqn): Try[Boolean] = TryWithResource(dbPool.acquire()) { db =>
    val queryString =
      """SELECT id 
        |FROM Domain 
        |WHERE 
        |  namespace = :namespace AND 
        |  domainId = :domainId""".stripMargin

    val query = new OSQLSynchQuery[ODocument](queryString)
    val params = Map("namespace" -> domainFqn.namespace, "domainId" -> domainFqn.domainId)
    val result: JavaList[ODocument] = db.command(query).execute(params.asJava)

    result.asScala.toList match {
      case first :: Nil => true
      case first :: rest => false // FIXME log
      case _ => false
    }
  }

  def getDomainByFqn(domainFqn: DomainFqn): Try[Option[Domain]] = TryWithResource(dbPool.acquire()) { db =>
    val queryString = "SELECT FROM Domain WHERE namespace = :namespace AND domainId = :domainId"
    val query = new OSQLSynchQuery[ODocument](queryString)

    val params = Map(
      "namespace" -> domainFqn.namespace,
      "domainId" -> domainFqn.domainId)

    val result: JavaList[ODocument] = db.command(query).execute(params.asJava)

    QueryUtil.mapSingleResult(result) { doc => doc.asDomain }
  }

  def getDomainById(id: String): Try[Option[Domain]] = TryWithResource(dbPool.acquire()) { db =>
    val query = new OSQLSynchQuery[ODocument]("SELECT * FROM Domain WHERE id = :id")
    val params = Map("id" -> id)
    val result: JavaList[ODocument] = db.command(query).execute(params.asJava)
    QueryUtil.mapSingleResult(result) { doc => doc.asDomain }
  }

  def getDomainsInNamespace(namespace: String): Try[List[Domain]] = TryWithResource(dbPool.acquire()) { db =>
    val query = new OSQLSynchQuery[ODocument]("SELECT FROM Domain WHERE namespace = :namespace")
    val params = Map("namespace" -> namespace)
    val result: JavaList[ODocument] = db.command(query).execute(params.asJava)
    result.asScala.toList map { doc => doc.asDomain }
  }

  def removeDomain(id: String): Try[Unit] = TryWithResource(dbPool.acquire()) { db =>
    val command = new OCommandSQL("DELETE FROM Domain WHERE id = :id")
    val params = Map("id" -> id)
    db.command(command).execute(params.asJava)
  }

  def updateDomain(newConfig: Domain): Try[Unit] = TryWithResource(dbPool.acquire()) { db =>
    val query = new OSQLSynchQuery[ODocument]("SELECT FROM Domain WHERE id = :id")
    val params = Map("id" -> newConfig.id)
    val result: JavaList[ODocument] = db.command(query).execute(params.asJava)

    result.asScala.toList match {
      case first :: rest => {
        first.merge(newConfig, false, false)
        db.save(first)
      }
      case Nil =>
    }
  }
}