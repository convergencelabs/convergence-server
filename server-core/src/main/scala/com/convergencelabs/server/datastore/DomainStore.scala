package com.convergencelabs.server.datastore

import scala.collection.JavaConverters.mapAsJavaMapConverter
import scala.util.Failure
import scala.util.Try

import com.convergencelabs.server.domain.Domain
import com.convergencelabs.server.domain.DomainFqn
import com.convergencelabs.server.domain.DomainStatus
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx
import com.orientechnologies.orient.core.id.ORID
import com.orientechnologies.orient.core.record.impl.ODocument
import com.orientechnologies.orient.core.sql.OCommandSQL
import com.orientechnologies.orient.core.storage.ORecordDuplicatedException

import DomainStore.Fields.Id
import DomainStore.Fields.Namespace
import grizzled.slf4j.Logging

object DomainStore {
  val ClassName = "Domain"
  val DomainNamespaceIdIndex = "Domain.namespace_id"

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

class DomainStore(dbProvider: DatabaseProvider)
    extends AbstractDatabasePersistence(dbProvider)
    with Logging {

  def createDomain(domainFqn: DomainFqn, displayName: String, ownerUsername: String): Try[Unit] = tryWithDb { db =>
    val domain = Domain(domainFqn, displayName, ownerUsername, DomainStatus.Initializing, "")
    DomainStore.domainToDoc(domain, db).map { doc =>
      db.save(doc)
      ()
    }.get
  } recoverWith {
    case e: ORecordDuplicatedException => handleDuplicateValue[Unit](e)
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
  
  def getDomainsByAccess(username: String): Try[List[Domain]] = tryWithDb { db =>
    // TODO: Merge these queries together
    val accessQuery =
      """SELECT expand(set(domain))
        |  FROM UserDomainRole
        |  WHERE user.username = :username AND
        |    role.permissions CONTAINS (id = 'domain-access')""".stripMargin
    val params = Map("username" -> username)
    val access = QueryUtil.query(accessQuery, params, db) map { DomainStore.docToDomain(_) }
    
    val ownershipQuery = "SELECT * FROM Domain WHERE owner.username = :username"
    val ownership = QueryUtil.query(ownershipQuery, params, db) map { DomainStore.docToDomain(_) }
    access.union(ownership)
  }

  def getDomainsInNamespace(namespace: String): Try[List[Domain]] = tryWithDb { db =>
    val query = "SELECT * FROM Domain WHERE namespace = :namespace"
    val params = Map(Namespace -> namespace)
    QueryUtil.query(query, params, db) map { DomainStore.docToDomain(_) }
  }

  def removeDomain(domainFqn: DomainFqn): Try[Unit] = tryWithDb { db =>
    val command = new OCommandSQL("DELETE FROM Domain WHERE namespace = :namespace AND id = :id")
    val params = Map(Namespace -> domainFqn.namespace, Id -> domainFqn.domainId)
    db.command(command).execute(params.asJava).asInstanceOf[Int] match {
      case 0 => 
        throw new EntityNotFoundException()
      case _ => 
        ()
    }
  }

  def updateDomain(domain: Domain): Try[Unit] = tryWithDb { db =>
    val query = "SELECT * FROM Domain WHERE namespace = :namespace AND id = :id"
    val params = Map(Namespace -> domain.domainFqn.namespace, Id -> domain.domainFqn.domainId)
    QueryUtil.lookupOptionalDocument(query, params, db) match {
      case Some(existing) =>
        DomainStore.domainToDoc(domain, db).map { updated =>
          existing.merge(updated, true, false)
          db.save(existing)
          ()
        }.get
      case _ =>
        throw new EntityNotFoundException()
    }
  }.recoverWith {
    case e: ORecordDuplicatedException => handleDuplicateValue[Unit](e)
  }
  
  private[this] def handleDuplicateValue[T](e: ORecordDuplicatedException):  Try[T] = {
      e.getIndexName match {
        case DomainStore.DomainNamespaceIdIndex =>
          Failure(DuplicateValueException("namespace_id"))
        case _ =>
          Failure(e)
      }
  }
}
