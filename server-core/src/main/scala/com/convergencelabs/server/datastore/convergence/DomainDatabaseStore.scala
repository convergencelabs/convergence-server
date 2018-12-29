package com.convergencelabs.server.datastore.convergence

import scala.util.Failure
import scala.util.Try

import com.convergencelabs.server.datastore.AbstractDatabasePersistence
import com.convergencelabs.server.db.DatabaseProvider
import com.convergencelabs.server.datastore.DuplicateValueException
import com.convergencelabs.server.datastore.OrientDBUtil
import com.convergencelabs.server.domain.DomainDatabase
import com.convergencelabs.server.domain.DomainFqn
import com.orientechnologies.orient.core.db.document.ODatabaseDocument
import com.orientechnologies.orient.core.record.impl.ODocument
import com.orientechnologies.orient.core.storage.ORecordDuplicatedException

import grizzled.slf4j.Logging

object DomainDatabaseStore {
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

  def domainDatabaseToDoc(domainDatabase: DomainDatabase, db: ODatabaseDocument): Try[ODocument] = {
    val DomainDatabase(fqn, database, username, password, adminUsername, adminPassword) = domainDatabase
    DomainStore.getDomainRid(fqn, db).recoverWith {
      case cause: Exception =>
        Failure(new IllegalArgumentException(
          s"Could store domain database info because domain does not exist: ${fqn}"))
    }.flatMap { domainLink =>
      Try {
      val doc = new ODocument(Schema.DomainDatabase.Class)
      doc.field(Fields.Domain, domainLink)
      doc.field(Fields.Database, database)
      doc.field(Fields.Username, username)
      doc.field(Fields.Password, password)
      doc.field(Fields.AdminUsername, adminUsername)
      doc.field(Fields.AdminPassword, adminPassword)
      doc
      }
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

  import DomainDatabaseStore._

  def createDomainDatabase(domainDatabase: DomainDatabase): Try[Unit] = withDb { db =>
    DomainDatabaseStore.domainDatabaseToDoc(domainDatabase, db).map { doc =>
      db.save(doc)
      ()
    } recoverWith (handleDuplicateValue)
  } 

  def removeDomainDatabase(domainFqn: DomainFqn): Try[Unit] = withDb { db =>
    OrientDBUtil.mutateOneDocument(
      db,
      "DELETE FROM DomainDatabase WHERE domain.namespace = :namespace AND domain.id = :domainId",
      Map(Constants.Namespace -> domainFqn.namespace, Constants.DomainId -> domainFqn.domainId))
  }

  def updateDomainDatabase(domainDatabase: DomainDatabase): Try[Unit] = withDb { db =>
    val query = "SELECT * FROM DomainDatabase WHERE domain.namespace = :namespace AND domain.id = :domainId"
    val params = Map(
      Constants.Namespace -> domainDatabase.domainFqn.namespace,
      Constants.DomainId -> domainDatabase.domainFqn.domainId)
    OrientDBUtil.getDocument(db, query, params).flatMap { existing =>
      DomainDatabaseStore.domainDatabaseToDoc(domainDatabase, db).map { updated =>
        existing.merge(updated, true, false)
        db.save(existing)
        ()
      }
    } recoverWith (handleDuplicateValue)
  }

  def getDomainDatabase(domainFqn: DomainFqn): Try[Option[DomainDatabase]] = withDb { db =>
    val query =
      """
       |SELECT *
       |FROM DomainDatabase 
       |WHERE 
       | domain.namespace = :namespace AND 
       | domain.id = :domainId""".stripMargin

    val params = Map(Constants.Namespace -> domainFqn.namespace, Constants.DomainId -> domainFqn.domainId)
    OrientDBUtil.findDocument(db, query, params).map(_.map(DomainDatabaseStore.docToDomainDatabase(_)))
  }

  def getAllDomainDatabasesForUser(username: String): Try[List[DomainDatabase]] = withDb { db =>
    val query = "SELECT * FROM DomainDatabase WHERE domain.owner.username = :username"
    val params = Map("username" -> username)
    OrientDBUtil
      .query(db, query, params)
      .map(_.map(DomainDatabaseStore.docToDomainDatabase(_)))
  }

  private[this] def handleDuplicateValue[T](): PartialFunction[Throwable, Try[T]] = {
    case e: ORecordDuplicatedException =>
      e.getIndexName match {
        case Schema.DomainDatabase.Indices.Domain =>
          Failure(DuplicateValueException("domain"))
        case Schema.DomainDatabase.Indices.Database =>
          Failure(DuplicateValueException("database"))
        case _ =>
          Failure(e)
      }
  }
}
