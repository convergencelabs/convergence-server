package com.convergencelabs.server.datastore

import java.util.{ List => JavaList }
import scala.util.Failure
import scala.util.Try
import com.convergencelabs.server.domain.DomainDatabase
import com.convergencelabs.server.domain.DomainFqn
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx
import com.orientechnologies.orient.core.record.impl.ODocument
import com.orientechnologies.orient.core.sql.OCommandSQL
import com.orientechnologies.orient.core.sql.query.OSQLSynchQuery
import com.orientechnologies.orient.core.storage.ORecordDuplicatedException
import grizzled.slf4j.Logging
import java.util.{List => JavaList}

import scala.collection.JavaConversions._
import scala.collection.JavaConverters._

import DomainDatabaseStore.Fields._
import DomainDatabaseStore.Constants._

object DomainDatabaseStore {
  val ClassName = "DomainDatabase"

  object Constants {
    val Namespace = "namespace"
    val DomainId = "domainId"
  }
  
  object Fields {
    val Domain = "domain"
    val Database = "database"
    val AdminUsername = "adminUsername"
    val AdminPassword = "adminPassword"
    val Username = "username"
    val Password = "password"
  }

  def domainDatabaseToDoc(domainDatabase: DomainDatabase, db: ODatabaseDocumentTx): Try[ODocument] = {
    val DomainDatabase(fqn, database, username, password, adminUsername, adminPassword) = domainDatabase
    DomainStore.getDomainRid(fqn, db).recoverWith {
      case cause: Exception =>
        Failure(new IllegalArgumentException(
          s"Could store domain database info because domain does not exist: ${fqn}"))
    }.map { domainLink =>
      val doc = new ODocument(ClassName)
      doc.field(Fields.Domain, domainLink)
      doc.field(Fields.Database, database)
      doc.field(Fields.Username, username)
      doc.field(Fields.Password, password)
      doc.field(Fields.AdminUsername, adminUsername)
      doc.field(Fields.AdminPassword, adminPassword)
      doc
    }
  }

  def docToDomainDatabase(doc: ODocument): DomainDatabase = {
    DomainDatabase(
      DomainFqn(doc.field("domain.namespace"), doc.field("domain.id")),
      doc.field(Fields.Database),
      doc.field(Fields.Username),
      doc.field(Fields.Password),
      doc.field(Fields.AdminUsername),
      doc.field(Fields.AdminPassword))
  }
}

class DomainDatabaseStore(dbProvider: DatabaseProvider)
    extends AbstractDatabasePersistence(dbProvider)
    with Logging {

  def createDomainDatabase(domainDatabase: DomainDatabase): Try[CreateResult[Unit]] = tryWithDb { db =>
    DomainDatabaseStore.domainDatabaseToDoc(domainDatabase, db).map { doc =>
      db.save(doc)
      CreateSuccess(())
    }.get
  } recover {
    case e: ORecordDuplicatedException =>
      DuplicateValue
  }
  
  def removeDomainDatabase(domainFqn: DomainFqn): Try[DeleteResult] = tryWithDb { db =>
    val command = new OCommandSQL("DELETE FROM DomainDatabase WHERE domain.namespace = :namespace AND domain.id = :domainId")
    val params = Map(
      Namespace -> domainFqn.namespace,
      DomainId -> domainFqn.domainId)
    val count: Int = db.command(command).execute(params.asJava)
    count match {
      case 0 => NotFound
      case _ => DeleteSuccess
    }
  }

  def updateDomainDatabase(domainDatabase: DomainDatabase): Try[UpdateResult] = tryWithDb { db =>
    val query = "SELECT * FROM DomainDatabase WHERE domain.namespace = :namespace AND domain.id = :domainId"
    val params = Map(
        Namespace -> domainDatabase.domainFqn.namespace, 
        DomainId -> domainDatabase.domainFqn.domainId)
    QueryUtil.lookupOptionalDocument(query, params, db) match {
      case Some(existing) =>
        DomainDatabaseStore.domainDatabaseToDoc(domainDatabase, db).map { updated =>
          existing.merge(updated, true, false)
          db.save(existing)
          UpdateSuccess
        }.get
      case _ =>
        NotFound
    }
  }.recover {
    case e: ORecordDuplicatedException =>
      // FIXME should this be duplicate value??
      InvalidValue
  }

  def getDomainDatabase(domainFqn: DomainFqn): Try[Option[DomainDatabase]] = tryWithDb { db =>
    val query = 
      """
       |SELECT *
       |FROM DomainDatabase 
       |WHERE 
       | domain.namespace = :namespace AND 
       | domain.id = :domainId""".stripMargin
       
    val params = Map(Namespace -> domainFqn.namespace, DomainId -> domainFqn.domainId)
    QueryUtil.lookupOptionalDocument(query, params, db) map { DomainDatabaseStore.docToDomainDatabase(_) }
  }

  def getAllDomainDatabasesForUser(username: String): Try[List[DomainDatabase]] = tryWithDb { db =>
    val queryString = "SELECT * FROM DomainDatabase WHERE domain.owner.username = :username"
    val query = new OSQLSynchQuery[ODocument](queryString)
    val params = Map("username" -> username)
    val result: JavaList[ODocument] = db.command(query).execute(params.asJava)
    result.asScala.toList map { DomainDatabaseStore.docToDomainDatabase(_) }
  }
}
