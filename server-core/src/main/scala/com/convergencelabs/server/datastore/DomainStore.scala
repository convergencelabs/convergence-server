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

  private[this] val Id = "id"
  private[this] val Namespace = "namespace"
  private[this] val DomainId = "domainId"
  private[this] val Owner = "owner"
  private[this] val Uid = "uid"
  private[this] val DisplayName = "displayName"
  private[this] val Status = "status"
  private[this] val DomainClassName = "Domain"
  
  val DidSeq = "DIDSEQ"
  

  def createDomain(domainFqn: DomainFqn, displayName: String, ownerUid: String, dbInfo: DomainDatabaseInfo): Try[CreateResult[String]] = tryWithDb { db =>
    //FIXME: Remove after figuring out how to create in schema
    if(!db.getMetadata().getSequenceLibrary().getSequenceNames.contains(DidSeq)) {
      val createParams = new CreateParams().setDefaults()
      db.getMetadata().getSequenceLibrary().createSequence(DidSeq, SEQUENCE_TYPE.CACHED, createParams)
    }
    
    val seq = db.getMetadata().getSequenceLibrary().getSequence(DidSeq)
    val did = seq.next().toString
    
    val query = new OSQLSynchQuery[ODocument]("SELECT FROM User WHERE uid = :uid")
    val params = Map(Uid -> ownerUid)

    val result: JavaList[ODocument] = db.command(query).execute(params.asJava)
    try {
      QueryUtil.enforceSingletonResultList(result) match {
        case Some(user) => {
          val doc = new ODocument(DomainClassName)
          doc.field(Id, did)
          doc.field(Namespace, domainFqn.namespace)
          doc.field(DomainId, domainFqn.domainId)
          doc.field(DisplayName, displayName)
          doc.field(Status, DomainStatus.Initializing.toString())
          doc.field(Owner, user)
          doc.field("dbName", dbInfo.database)
          doc.field("dbUsername", dbInfo.username)
          doc.field("dbPassword", dbInfo.password)
          db.save(doc)
          CreateSuccess(did)
        }
        case None => InvalidValue
      }
    } catch {
      case e: ORecordDuplicatedException => DuplicateValue
    }
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

  def getDomainByFqn(domainFqn: DomainFqn): Try[Option[Domain]] = tryWithDb { db =>
    val queryString = "SELECT FROM Domain WHERE namespace = :namespace AND domainId = :domainId"
    val query = new OSQLSynchQuery[ODocument](queryString)

    val params = Map(
      Namespace -> domainFqn.namespace,
      DomainId -> domainFqn.domainId)

    val result: JavaList[ODocument] = db.command(query).execute(params.asJava)

    QueryUtil.mapSingletonList(result) { doc => doc.asDomain }
  }

  def getDomainsByOwner(uid: String): Try[List[Domain]] = tryWithDb { db =>
    val query = new OSQLSynchQuery[ODocument]("SELECT FROM Domain WHERE owner.uid = :uid")
    val params = Map("uid" -> uid)
    val result: JavaList[ODocument] = db.command(query).execute(params.asJava)
    result.asScala.toList map { doc => doc.asDomain }
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

  def removeDomain(id: String): Try[DeleteResult] = tryWithDb { db =>
    val command = new OCommandSQL("DELETE FROM Domain WHERE id = :id")
    val params = Map(Id -> id)
    val count: Int = db.command(command).execute(params.asJava)
    count match {
      case 0 => NotFound
      case _ => DeleteSuccess
    }
  }

  def updateDomain(domain: Domain): Try[UpdateResult] = tryWithDb { db =>
    val query = new OSQLSynchQuery[ODocument]("SELECT FROM Domain WHERE id = :id")
    val params = Map(Id -> domain.id)
    val result: JavaList[ODocument] = db.command(query).execute(params.asJava)

    QueryUtil.enforceSingletonResultList(result) match {
      case Some(doc) => {
        val newDoc = domain.asODocument
        newDoc.removeField("owner")
        doc.merge(domain, true, false)
        db.save(doc)
        UpdateSuccess
      }
      case None => NotFound
    }
  }
}
