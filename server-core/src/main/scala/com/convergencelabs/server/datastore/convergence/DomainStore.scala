package com.convergencelabs.server.datastore.convergence

import scala.util.Failure
import scala.util.Try

import com.convergencelabs.server.datastore.AbstractDatabasePersistence
import com.convergencelabs.server.db.DatabaseProvider
import com.convergencelabs.server.datastore.DuplicateValueException
import com.convergencelabs.server.datastore.OrientDBUtil
import com.convergencelabs.server.domain.Domain
import com.convergencelabs.server.domain.DomainFqn
import com.convergencelabs.server.domain.DomainStatus
import com.orientechnologies.orient.core.db.document.ODatabaseDocument
import com.orientechnologies.orient.core.id.ORID
import com.orientechnologies.orient.core.record.impl.ODocument
import com.orientechnologies.orient.core.storage.ORecordDuplicatedException

import grizzled.slf4j.Logging

object DomainStore {
  
  object Fields {
    val Namespace = "namespace"
    val Id = "id"
    val DisplayName = "displayName"
    val Owner = "owner"
    val Status = "status"
    val StatusMessage = "statusMessage"
  }

  def domainToDoc(domain: Domain, db: ODatabaseDocument): Try[ODocument] = {
    val Domain(DomainFqn(namespace, domainId), displayName, ownerUsername, status, statusMessage) = domain

    UserStore.getUserRid(ownerUsername, db).recoverWith {
      case cause: Exception =>
        Failure(new IllegalArgumentException(
          s"Could not create/update domain because the owner could not be found: ${ownerUsername}"))
    }.map { ownerLink =>
      val doc = db.newInstance(Schema.Domain.Class).asInstanceOf[ODocument]
      doc.setProperty(Fields.Id, domainId)
      doc.setProperty(Fields.Namespace, namespace)
      doc.setProperty(Fields.Owner, ownerLink)
      doc.setProperty(Fields.DisplayName, displayName)
      doc.setProperty(Fields.Status, status.toString())
      doc.setProperty(Fields.StatusMessage, statusMessage)
      doc
    }
  }

  def docToDomain(doc: ODocument): Domain = {
    val status: DomainStatus.Value = DomainStatus.withName(doc.field(Fields.Status))
    val username: String = doc.field("owner.username")
    Domain(
      DomainFqn(doc.field(Fields.Namespace), doc.field(Fields.Id)),
      doc.getProperty(Fields.DisplayName),
      username,
      status,
      doc.field(Fields.StatusMessage))
  }

  def getDomainRid(domainFqn: DomainFqn, db: ODatabaseDocument): Try[ORID] = {
    getDomainRid(domainFqn.namespace, domainFqn.domainId, db)
  }

  def getDomainRid(namespace: String, domainId: String, db: ODatabaseDocument): Try[ORID] = {
    OrientDBUtil.getIdentityFromSingleValueIndex(db, Schema.Domain.Indices.NamespaceId, List(namespace, domainId))
  }
}

class DomainStore(dbProvider: DatabaseProvider)
  extends AbstractDatabasePersistence(dbProvider)
  with Logging {

  import DomainStore._

  def createDomain(domainFqn: DomainFqn, displayName: String, ownerUsername: String): Try[Unit] = tryWithDb { db =>
    val domain = Domain(domainFqn, displayName, ownerUsername, DomainStatus.Initializing, "")
    DomainStore.domainToDoc(domain, db).map { doc =>
      db.save(doc)
      ()
    }.get
  } recoverWith (handleDuplicateValue)

  def domainExists(domainFqn: DomainFqn): Try[Boolean] = withDb { db =>
    OrientDBUtil.indexContains(db, Schema.Domain.Indices.NamespaceId, List(domainFqn.namespace, domainFqn.domainId))
  }

  def getDomainByFqn(domainFqn: DomainFqn): Try[Option[Domain]] = withDb { db =>
    OrientDBUtil
      .findDocument(
        db,
        "SELECT * FROM Domain WHERE namespace = :namespace AND id = :id",
        Map(Fields.Namespace -> domainFqn.namespace, Fields.Id -> domainFqn.domainId))
      .map(_.map(DomainStore.docToDomain(_)))
  }

  def getDomainsByOwner(username: String): Try[List[Domain]] = withDb { db =>
    OrientDBUtil
      .query(
        db,
        "SELECT * FROM Domain WHERE owner.username = :username",
        Map("username" -> username))
      .map(_.map(DomainStore.docToDomain(_)))
  }

  def getDomainsByAccess(username: String): Try[List[Domain]] = withDb { db =>
    // TODO: Merge these queries together
    val accessQuery =
      """SELECT expand(set(domain))
        |  FROM UserDomainRole
        |  WHERE user.username = :username AND
        |    role.permissions CONTAINS (id = 'domain-access')""".stripMargin
    val ownershipQuery = "SELECT * FROM Domain WHERE owner.username = :username"
    for {
      access <- OrientDBUtil.query(db, accessQuery, Map("username" -> username)).map(_.map(DomainStore.docToDomain(_)))
      ownership <- OrientDBUtil.query(db, ownershipQuery, Map("username" -> username)).map(_.map(DomainStore.docToDomain(_)))
    } yield {
      access.union(ownership)
    }
  }

  def getDomainsInNamespace(namespace: String): Try[List[Domain]] = withDb { db =>
    OrientDBUtil
      .query(db, "SELECT * FROM Domain WHERE namespace = :namespace", Map(Fields.Namespace -> namespace))
      .map(_.map(DomainStore.docToDomain(_)))
  }

  def removeDomain(domainFqn: DomainFqn): Try[Unit] = withDb { db =>
    OrientDBUtil.mutateOneDocument(
      db,
      "DELETE FROM Domain WHERE namespace = :namespace AND id = :id",
      Map(Fields.Namespace -> domainFqn.namespace, Fields.Id -> domainFqn.domainId))
  }

  def updateDomain(domain: Domain): Try[Unit] = withDb { db =>
    OrientDBUtil
      .getDocument(
        db,
        "SELECT * FROM Domain WHERE namespace = :namespace AND id = :id",
        Map(Fields.Namespace -> domain.domainFqn.namespace, Fields.Id -> domain.domainFqn.domainId))
      .flatMap { existing =>
        DomainStore.domainToDoc(domain, db).map { updated =>
          existing.merge(updated, true, false)
          db.save(existing)
          ()
        }
      } recoverWith (handleDuplicateValue)
  }

  private[this] def handleDuplicateValue[T](): PartialFunction[Throwable, Try[T]] = {
    case e: ORecordDuplicatedException =>
      e.getIndexName match {
        case Schema.Domain.Indices.NamespaceId =>
          Failure(DuplicateValueException("namespace_id"))
        case _ =>
          Failure(e)
      }
  }
}
