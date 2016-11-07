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
import com.convergencelabs.server.domain.DomainDatabaseInfo
import com.convergencelabs.server.domain.DomainDatabaseInfo
import com.orientechnologies.orient.core.exception.OValidationException
import com.orientechnologies.orient.core.storage.ORecordDuplicatedException
import com.orientechnologies.orient.core.metadata.sequence.OSequence.SEQUENCE_TYPE
import com.orientechnologies.orient.core.metadata.sequence.OSequence.CreateParams
import com.convergencelabs.server.domain.DomainStatus

class DomainStore (dbPool: OPartitionedDatabasePool)
    extends AbstractDatabasePersistence(dbPool)
    with Logging {

  private[this] val Namespace = "namespace"
  private[this] val DomainId = "domainId"
  private[this] val Owner = "owner"
  private[this] val Uid = "uid"
  private[this] val DisplayName = "displayName"
  private[this] val Status = "status"
  private[this] val StatusMessage = "statusMessage"
  private[this] val DomainClassName = "Domain"
  
  val DidSeq = "DIDSEQ"
  

  def createDomain(domainFqn: DomainFqn, displayName: String, ownerUsername: String, dbInfo: DomainDatabaseInfo): Try[CreateResult[Unit]] = tryWithDb { db =>
    val query = new OSQLSynchQuery[ODocument]("SELECT FROM User WHERE username = :username")
    val params = Map("username" -> ownerUsername)

    val result: JavaList[ODocument] = db.command(query).execute(params.asJava)
    try {
      QueryUtil.enforceSingletonResultList(result) match {
        case Some(user) => {
          val doc = new ODocument(DomainClassName)
          doc.field(Namespace, domainFqn.namespace)
          doc.field(DomainId, domainFqn.domainId)
          doc.field(DisplayName, displayName)
          doc.field(Status, DomainStatus.Initializing.toString())
          doc.field(StatusMessage, "")
          doc.field(Owner, user)
          doc.field("dbName", dbInfo.database)
          doc.field("dbUsername", dbInfo.username)
          doc.field("dbPassword", dbInfo.password)
          db.save(doc)
          CreateSuccess(())
        }
        case None => InvalidValue
      }
    } catch {
      case e: ORecordDuplicatedException => DuplicateValue
    }
  }

  def domainExists(domainFqn: DomainFqn): Try[Boolean] = tryWithDb { db =>
    val queryString =
      """SELECT *
        |FROM Domain
        |WHERE
        |  namespace = :namespace AND
        |  domainId = :domainId""".stripMargin

    val query = new OSQLSynchQuery[ODocument](queryString)
    val params = Map(Namespace -> domainFqn.namespace, DomainId -> domainFqn.domainId)
    val result: JavaList[ODocument] = db.command(query).execute(params.asJava)

    QueryUtil.enforceSingletonResultList(result) match {
      case Some(_) => true
      case None    => false
    }
  }

  def getDomainDatabaseInfo(domainFqn: DomainFqn): Try[Option[DomainDatabaseInfo]] = tryWithDb { db =>
    val queryString = "SELECT dbName, dbUsername, dbPassword FROM Domain WHERE namespace = :namespace AND domainId = :domainId"
    val query = new OSQLSynchQuery[ODocument](queryString)

    val params = Map(
      Namespace -> domainFqn.namespace,
      DomainId -> domainFqn.domainId)

    val result: JavaList[ODocument] = db.command(query).execute(params.asJava)

    QueryUtil.mapSingletonList(result) { doc =>
      DomainDatabaseInfo(doc.field("dbName"), doc.field("dbUsername"), doc.field("dbPassword"))
    }
  }
  
  def getAllDomainInfoForUser(username: String): Try[List[(Domain,DomainDatabaseInfo)]] = tryWithDb { db =>
    val queryString = "SELECT * FROM Domain WHERE owner.username = :username"
    val query = new OSQLSynchQuery[ODocument](queryString)
    val params = Map("username" -> username)

    val result: JavaList[ODocument] = db.command(query).execute(params.asJava)
    result.asScala.toList map { doc =>
      val domain = doc.asDomain
      val info = DomainDatabaseInfo(doc.field("dbName"), doc.field("dbUsername"), doc.field("dbPassword"))
      (domain, info)
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

  def getDomainsByOwner(username: String): Try[List[Domain]] = tryWithDb { db =>
    val query = new OSQLSynchQuery[ODocument]("SELECT FROM Domain WHERE owner.username = :username")
    val params = Map("username" -> username)
    val result: JavaList[ODocument] = db.command(query).execute(params.asJava)
    result.asScala.toList map { doc => doc.asDomain }
  }

  def getDomainsInNamespace(namespace: String): Try[List[Domain]] = tryWithDb { db =>
    val query = new OSQLSynchQuery[ODocument]("SELECT FROM Domain WHERE namespace = :namespace")
    val params = Map(Namespace -> namespace)
    val result: JavaList[ODocument] = db.command(query).execute(params.asJava)
    result.asScala.toList map { doc => doc.asDomain }
  }

  def removeDomain(domainFqn: DomainFqn): Try[DeleteResult] = tryWithDb { db =>
    val command = new OCommandSQL("DELETE FROM Domain WHERE namespace = :namespace AND domainId = :domainId")
    val params = Map(
        Namespace -> domainFqn.namespace,
        DomainId -> domainFqn.domainId)
    val count: Int = db.command(command).execute(params.asJava)
    count match {
      case 0 => NotFound
      case _ => DeleteSuccess
    }
  }

  def updateDomain(domain: Domain): Try[UpdateResult] = tryWithDb { db =>
    val query = new OSQLSynchQuery[ODocument]("SELECT FROM Domain WHERE namespace = :namespace AND domainId = :domainId")
    val params = Map(
        Namespace -> domain.domainFqn.namespace,
        DomainId -> domain.domainFqn.domainId)
    val result: JavaList[ODocument] = db.command(query).execute(params.asJava)

    QueryUtil.enforceSingletonResultList(result) match {
      case Some(doc) => {
        val newDoc = domain.asODocument
        // FIXME what is this for?
        newDoc.removeField("owner")
        doc.merge(domain, true, false)
        db.save(doc)
        UpdateSuccess
      }
      case None => NotFound
    }
  }
}
