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

object DomainStore {
  val ClassName = "Domain"

  object Fields {
    val Namespace = "namespace"
    val DomainId = "domainId"
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
      val doc = new ODocument(ClassName)
      doc.field(Fields.Namespace, namespace)
      doc.field(Fields.Owner, ownerLink)
      doc.field(Fields.DomainId, domainId)
      doc.field(Fields.DisplayName, displayName)
      doc.field(Fields.Status, status.toString())
      doc.field(Fields.StatusMessage, statusMessage)
      doc
    }
  }

  def docToDomain(doc: ODocument): Domain = {
    val status: DomainStatus.Value = DomainStatus.withName(doc.field(Fields.Status))
    Domain(
      DomainFqn(doc.field(Fields.Namespace), doc.field(Fields.DomainId)),
      doc.field(Fields.DisplayName),
      doc.field(Fields.Owner),
      status,
      doc.field(Fields.StatusMessage))
  }

  def getDomainRid(domainFqn: DomainFqn, db: ODatabaseDocumentTx): Try[ORID] = {
    getDomainRid(domainFqn.namespace, domainFqn.domainId, db)
  }

  def getDomainRid(namespace: String, domainId: String, db: ODatabaseDocumentTx): Try[ORID] = {
    val query = "SELECT @RID as rid FROM Domain WHERE id = :id AND namespace = :namespace"
    val params = Map("id" -> domainId, "namespace" -> namespace)
    QueryUtil.lookupMandatoryDocument(query, params, db) map { doc =>
      val ridDoc: ODocument = doc.field("rid")
      val rid = ridDoc.getIdentity
      rid
    }
  }
}

class DomainStore(dbPool: OPartitionedDatabasePool)
    extends AbstractDatabasePersistence(dbPool)
    with Logging {

  private[this] val Namespace = "namespace"
  private[this] val DomainId = "domainId"
  private[this] val Owner = "owner"
  private[this] val DisplayName = "displayName"
  private[this] val Status = "status"
  private[this] val StatusMessage = "statusMessage"
  private[this] val DomainClassName = "Domain"

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
    val queryString =
      """SELECT id
        |FROM Domain
        |WHERE
        |  namespace = :namespace AND
        |  id = :domainId""".stripMargin

    val query = new OSQLSynchQuery[ODocument](queryString)
    val params = Map(Namespace -> domainFqn.namespace, DomainId -> domainFqn.domainId)
    val result: JavaList[ODocument] = db.command(query).execute(params.asJava)

    QueryUtil.enforceSingletonResultList(result) match {
      case Some(_) => true
      case None => false
    }
  }

  def getDomainByFqn(domainFqn: DomainFqn): Try[Option[Domain]] = tryWithDb { db =>
    val queryString = "SELECT FROM Domain WHERE namespace = :namespace AND id = :domainId"
    val query = new OSQLSynchQuery[ODocument](queryString)

    val params = Map(
      Namespace -> domainFqn.namespace,
      DomainId -> domainFqn.domainId)

    val result: JavaList[ODocument] = db.command(query).execute(params.asJava)
    QueryUtil.mapSingletonList(result) { DomainStore.docToDomain(_) }
  }

  def getDomainsByOwner(username: String): Try[List[Domain]] = tryWithDb { db =>
    val query = new OSQLSynchQuery[ODocument]("SELECT FROM Domain WHERE owner.username = :username")
    val params = Map("username" -> username)
    val result: JavaList[ODocument] = db.command(query).execute(params.asJava)
    result.asScala.toList map { DomainStore.docToDomain(_) }
  }

  def getDomainsInNamespace(namespace: String): Try[List[Domain]] = tryWithDb { db =>
    val query = new OSQLSynchQuery[ODocument]("SELECT FROM Domain WHERE namespace = :namespace")
    val params = Map(Namespace -> namespace)
    val result: JavaList[ODocument] = db.command(query).execute(params.asJava)
    result.asScala.toList map { DomainStore.docToDomain(_) }
  }

  def removeDomain(domainFqn: DomainFqn): Try[DeleteResult] = tryWithDb { db =>
    val command = new OCommandSQL("DELETE FROM Domain WHERE namespace = :namespace AND id = :domainId")
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
    val query = "SELECT FROM Domain WHERE namespace = :namespace AND id = :domainId"
    val params = Map(Namespace -> domain.domainFqn.namespace, DomainId -> domain.domainFqn.domainId)
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
