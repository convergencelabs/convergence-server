package com.convergencelabs.server.datastore

import java.util.{ List => JavaList }

import scala.collection.JavaConverters.asScalaBufferConverter
import scala.collection.JavaConverters.mapAsJavaMapConverter
import scala.util.Try

import com.convergencelabs.server.domain.Domain
import com.convergencelabs.server.domain.DomainFqn
import com.convergencelabs.server.domain.DomainStatus
import com.orientechnologies.orient.core.db.OPartitionedDatabasePool
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx
import com.orientechnologies.orient.core.id.ORID
import com.orientechnologies.orient.core.record.impl.ODocument
import com.orientechnologies.orient.core.sql.OCommandSQL
import com.orientechnologies.orient.core.sql.query.OSQLSynchQuery
import com.orientechnologies.orient.core.storage.ORecordDuplicatedException

import grizzled.slf4j.Logging
import scala.util.Failure
import scala.util.control.NonFatal
import scala.util.Success

import DomainStore.Fields._
import com.orientechnologies.orient.core.db.record.OIdentifiable

object DomainStore {
  val ClassName = "Domain"

  object Fields {
    val Namespace = "namespace"
    val Id = "id"
    val DisplayName = "displayName"
    val Owner = "owner"
    val Status = "status"
    val StatusMessage = "statusMessage"
  }

  def domainToDoc(domain: Domain, db: ODatabaseDocumentTx): Try[ODocument] = {
    val Domain(DomainFqn(namespace, domainId), displayName, ownerUsername, status, statusMessage) = domain

    UserStore.getUserRid(ownerUsername, db).recoverWith {
      case cause: Exception =>
        Failure(new IllegalArgumentException(
          s"Could not create/update domain because the owner could not be found: ${ownerUsername}"))
    }.map { ownerLink =>
      val doc = db.newInstance(ClassName)
      doc.field(Fields.Id, domainId)
      doc.field(Fields.Namespace, namespace)
      doc.field(Fields.Owner, ownerLink)
      doc.field(Fields.DisplayName, displayName)
      doc.field(Fields.Status, status.toString())
      doc.field(Fields.StatusMessage, statusMessage)
      doc
    }
  }

  def docToDomain(doc: ODocument): Domain = {
    val status: DomainStatus.Value = DomainStatus.withName(doc.field(Fields.Status))
    val username: String = doc.field("owner.username") 
    Domain(
      DomainFqn(doc.field(Fields.Namespace), doc.field(Fields.Id)),
      doc.field(Fields.DisplayName),
      username,
      status,
      doc.field(Fields.StatusMessage))
  }

  def getDomainRid(domainFqn: DomainFqn, db: ODatabaseDocumentTx): Try[ORID] = {
    getDomainRid(domainFqn.namespace, domainFqn.domainId, db)
  }

  def getDomainRid(namespace: String, domainId: String, db: ODatabaseDocumentTx): Try[ORID] = {
    val query = "SELECT @rid as rid FROM Domain WHERE id = :id AND namespace = :namespace"
    val params = Map("id" -> domainId, "namespace" -> namespace)
    QueryUtil.lookupMandatoryDocument(query, params, db) map { _.eval("rid").asInstanceOf[ORID] }
  }
}

class DomainStore(dbPool: OPartitionedDatabasePool)
    extends AbstractDatabasePersistence(dbPool)
    with Logging {

  def createDomain(domainFqn: DomainFqn, displayName: String, ownerUsername: String): Try[CreateResult[Unit]] = tryWithDb { db =>
    val domain = Domain(domainFqn, displayName, ownerUsername, DomainStatus.Initializing, "")
    DomainStore.domainToDoc(domain, db).map { doc =>
      db.save(doc)
      CreateSuccess(())
    }.get
  } recover {
    case e: ORecordDuplicatedException =>
      DuplicateValue
  }

  def domainExists(domainFqn: DomainFqn): Try[Boolean] = tryWithDb { db =>
    val query =
      """SELECT id
        |FROM Domain
        |WHERE
        |  namespace = :namespace AND
        |  id = :id""".stripMargin
    val params = Map(Namespace -> domainFqn.namespace, Id -> domainFqn.domainId)
    QueryUtil.lookupOptionalDocument(query, params, db) map (_ => true)  getOrElse (false)
  }

  def getDomainByFqn(domainFqn: DomainFqn): Try[Option[Domain]] = tryWithDb { db =>
//    val query = "SELECT * FROM Domain WHERE namespace = :namespace AND id = :id FETCHPLAN *:0 owner.username:0"
    val query = "SELECT * FROM Domain WHERE namespace = :namespace AND id = :id"
    val params = Map(Namespace -> domainFqn.namespace, Id -> domainFqn.domainId)
    QueryUtil.lookupOptionalDocument(query, params, db) map { DomainStore.docToDomain(_) }
  }

  def getDomainsByOwner(username: String): Try[List[Domain]] = tryWithDb { db =>
    val query = "SELECT * FROM Domain WHERE owner.username = :username"
    val params = Map("username" -> username)
    QueryUtil.query(query, params, db) map { DomainStore.docToDomain(_) }
  }

  def getDomainsInNamespace(namespace: String): Try[List[Domain]] = tryWithDb { db =>
    val query = "SELECT * FROM Domain WHERE namespace = :namespace"
    val params = Map(Namespace -> namespace)
    QueryUtil.query(query, params, db) map { DomainStore.docToDomain(_) }
  }

  def removeDomain(domainFqn: DomainFqn): Try[DeleteResult] = tryWithDb { db =>
    val command = new OCommandSQL("DELETE FROM Domain WHERE namespace = :namespace AND id = :id")
    val params = Map(Namespace -> domainFqn.namespace, Id -> domainFqn.domainId)
    db.command(command).execute(params.asJava).asInstanceOf[Int] match {
      case 0 => NotFound
      case _ => DeleteSuccess
    }
  }

  def updateDomain(domain: Domain): Try[UpdateResult] = tryWithDb { db =>
    val query = "SELECT * FROM Domain WHERE namespace = :namespace AND id = :id"
    val params = Map(Namespace -> domain.domainFqn.namespace, Id -> domain.domainFqn.domainId)
    QueryUtil.lookupOptionalDocument(query, params, db) match {
      case Some(existing) =>
        DomainStore.domainToDoc(domain, db).map { updated =>
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
}
