package com.convergencelabs.server.datastore.convergence

import scala.util.Failure
import scala.util.Try

import com.convergencelabs.server.datastore.AbstractDatabasePersistence
import com.convergencelabs.server.datastore.DuplicateValueException
import com.convergencelabs.server.datastore.OrientDBUtil
import com.convergencelabs.server.datastore.convergence.schema.DomainClass
import com.convergencelabs.server.db.DatabaseProvider
import com.convergencelabs.server.domain.Domain
import com.convergencelabs.server.domain.DomainFqn
import com.convergencelabs.server.domain.DomainStatus
import com.orientechnologies.orient.core.db.document.ODatabaseDocument
import com.orientechnologies.orient.core.id.ORID
import com.orientechnologies.orient.core.record.impl.ODocument
import com.orientechnologies.orient.core.storage.ORecordDuplicatedException

import grizzled.slf4j.Logging
import com.convergencelabs.server.domain.DomainDatabase
import com.convergencelabs.server.datastore.EntityNotFoundException

object DomainStore {

  object Params {
    val Namespace = "namespace"
    val Id = "id"
    val DisplayName = "displayName"
    val Status = "status"
    val StatusMessage = "statusMessage"
    val Filter = "filter"
    val Username = "Username"
  }

  def domainToDoc(domain: Domain, db: ODatabaseDocument): Try[ODocument] = {
    val Domain(DomainFqn(namespace, domainId), displayName, status, statusMessage) = domain
    NamespaceStore.getNamespaceRid(namespace, db).map { nsRid =>
      val doc = db.newInstance(DomainClass.ClassName).asInstanceOf[ODocument]
      doc.setProperty(DomainClass.Fields.Id, domainId)
      doc.setProperty(DomainClass.Fields.Namespace, nsRid)
      doc.setProperty(DomainClass.Fields.DisplayName, displayName)
      doc.setProperty(DomainClass.Fields.Status, status.toString())
      doc.setProperty(DomainClass.Fields.StatusMessage, statusMessage)
      doc
    }.recoverWith {
      case cause: EntityNotFoundException =>
        Failure(NamespaceNotFoundException(namespace))
    }
  }

  def docToDomain(doc: ODocument): Domain = {
    val status: DomainStatus.Value = DomainStatus.withName(doc.field(DomainClass.Fields.Status))
    val namespace = doc.eval("namespace.id").asInstanceOf[String]
    val fqn = DomainFqn(namespace, doc.getProperty(DomainClass.Fields.Id))
    val displayName = doc.getProperty(DomainClass.Fields.DisplayName).asInstanceOf[String]
    val statusMessage = doc.field(DomainClass.Fields.StatusMessage).asInstanceOf[String]
    Domain(fqn, displayName, status, statusMessage)
  }

  def getDomainRid(domainFqn: DomainFqn, db: ODatabaseDocument): Try[ORID] = {
    getDomainRid(domainFqn.namespace, domainFqn.domainId, db)
  }

  private[this] val DomainRidQuery = "SELECT @rid FROM Domain WHERE id = :id AND namespace.id = :namespace"
  def getDomainRid(namespace: String, domainId: String, db: ODatabaseDocument): Try[ORID] = {
    val params = Map(Params.Id -> domainId, Params.Namespace -> namespace)
    OrientDBUtil.getDocument(db, DomainRidQuery, params).map(_.getProperty("@rid").asInstanceOf[ORID])
  }

  def addDomainDatabaseFields(doc: ODocument, domainDatabase: DomainDatabase): Unit = {
    val DomainDatabase(database, username, password, adminUsername, adminPassword) = domainDatabase
    doc.field(DomainClass.Fields.DatabaseName, database)
    doc.field(DomainClass.Fields.DatabaseUsername, username)
    doc.field(DomainClass.Fields.DatabasePassword, password)
    doc.field(DomainClass.Fields.DatabaseAdminUsername, adminUsername)
    doc.field(DomainClass.Fields.DatabaseAdminPassword, adminPassword)
    ()
  }

  def docToDomainDatabase(doc: ODocument): DomainDatabase = {
    DomainDatabase(
      doc.field(DomainClass.Fields.DatabaseName),
      doc.field(DomainClass.Fields.DatabaseUsername),
      doc.field(DomainClass.Fields.DatabasePassword),
      doc.field(DomainClass.Fields.DatabaseAdminUsername),
      doc.field(DomainClass.Fields.DatabaseAdminPassword))
  }
}

class DomainStore(dbProvider: DatabaseProvider)
  extends AbstractDatabasePersistence(dbProvider)
  with Logging {

  import DomainStore._

  def createDomain(domainFqn: DomainFqn, displayName: String, domainDatabase: DomainDatabase): Try[Unit] = withDb { db =>
    val domain = Domain(domainFqn, displayName, DomainStatus.Initializing, "")
    domainToDoc(domain, db).map { doc =>
      addDomainDatabaseFields(doc, domainDatabase)
      db.save(doc)
      ()
    }
  } recoverWith (handleDuplicateValue)

  private[this] val DomainExistsQuery = "SELECT count(@rid) as count FROM Domain WHERE id = :id AND namespace.id = :namespace"
  def domainExists(domainFqn: DomainFqn): Try[Boolean] = withDb { db =>
    val DomainFqn(namespace, domainId) = domainFqn
    val params = Map(Params.Id -> domainId, Params.Namespace -> namespace)
    OrientDBUtil.getDocument(db, DomainExistsQuery, params).map(_.getProperty("count").asInstanceOf[Long] > 0)
  }

  private[this] val GetDomainQuery = "SELECT FROM Domain WHERE namespace.id = :namespace AND id = :id"
  def getDomainByFqn(domainFqn: DomainFqn): Try[Option[Domain]] = withDb { db =>
    val DomainFqn(namespace, domainId) = domainFqn
    val params = Map(Params.Id -> domainId, Params.Namespace -> namespace)
    OrientDBUtil.findDocument(db, GetDomainQuery, params).map(_.map(docToDomain(_)))
  }

  private[this] val GetDomainsByNamesapceQuery = "SELECT FROM Domain WHERE namespace.id = :namespace"
  def getDomainsInNamespace(namespace: String): Try[List[Domain]] = withDb { db =>
    val params = Map(Params.Namespace -> namespace)
    OrientDBUtil.query(db, GetDomainsByNamesapceQuery, params).map(_.map(docToDomain(_)))
  }

  def getDomains(namespace: Option[String], filter: Option[String], offset: Option[Int], limit: Option[Int]): Try[List[Domain]] = withDb { db =>
    val baseQuery = "SELECT FROM Domain"
    val (filterWhere, filterParams) = filter.map(filter => {
      val where = " (id.toLowerCase() LIKE :filter OR displayName.toLowerCase() LIKE :filter)"
      val params = Map[String, Any](Params.Filter -> s"%${filter}%")
      (where, params)
    }).getOrElse("", Map[String, Any]())

    val (namespaceWhere, namespaceParams) = namespace.map(ns => {
      val where = " namespace.id = :namespace"
      val params = Map[String, Any](Params.Namespace -> ns)
      (where, params)
    }).getOrElse("", Map[String, Any]())

    val whereClause = (namespace, filter) match {
      case (Some(n), Some(f)) =>
        " WHERE" + namespaceWhere + " AND" + filterWhere
      case (Some(n), None) =>
        " WHERE" + namespaceWhere
      case (None, Some(f)) =>
        " WHERE" + filterWhere
      case _ =>
        ""
    }

    val params = filterParams ++ namespaceParams
    val query = OrientDBUtil.buildPagedQuery(baseQuery + whereClause, limit, offset)
    OrientDBUtil.query(db, query, params).map(_.map(DomainStore.docToDomain(_)))
  }

  def getDomainsByAccess(username: String, namespace: Option[String], filter: Option[String], offset: Option[Int], limit: Option[Int]): Try[List[Domain]] = withDb { db =>
    val accessQuery = """
        |SELECT
        |  expand(set(domain))
        |FROM 
        |  UserRole
        |WHERE 
        |  user.username = :username AND
        |  (
        |    (role.permissions CONTAINS ('domain-access') AND target.@class = 'Domain') OR
        |    (role.permissions CONTAINS ('namespace-access') AND target.@class = 'Namespace') OR
        |    (role.permissions CONTAINS ('manage-domains') AND target IS NULL)
        |  )""".stripMargin

    val (filterWhere, filterParams) = filter.map(filter => {
      val where = " AND (id.toLowerCase() LIKE :filter OR displayName.toLowerCase() LIKE :filter)"
      val params = Map[String, String](Params.Filter -> s"%${filter}%")
      (where, params)
    }).getOrElse("", Map[String, Any]())

    val (namespaceWhere, namespaceParams) = namespace.map(ns => {
      val where = " AND namespace.id = :namespace"
      val params = Map[String, String](Params.Namespace -> ns)
      (where, params)
    }).getOrElse("", Map[String, Any]())

    val baseQuery = accessQuery + filterWhere + namespaceWhere

    val query = OrientDBUtil.buildPagedQuery(baseQuery, limit, offset)
    val params = Map(Params.Username -> username) ++ filterParams ++ namespaceParams
    OrientDBUtil.query(db, query, params).map(_.map(DomainStore.docToDomain(_)))
  }

  private[this] val DeleteDomainCommand = "DELETE FROM Domain WHERE namespace.id = :namespace AND id = :id"
  def removeDomain(domainFqn: DomainFqn): Try[Unit] = withDb { db =>
    val DomainFqn(namespace, domainId) = domainFqn
    val params = Map(Params.Id -> domainId, Params.Namespace -> namespace)
    OrientDBUtil.mutateOneDocument(db, DeleteDomainCommand, params)
  }

  def updateDomain(domain: Domain): Try[Unit] = withDb { db =>
    val params = Map(Params.Namespace -> domain.domainFqn.namespace, Params.Id -> domain.domainFqn.domainId)
    OrientDBUtil.getDocument(db, GetDomainQuery, params).flatMap { existing =>
      DomainStore.domainToDoc(domain, db).map { updated =>
        existing.merge(updated, true, false)
        db.save(existing)
        ()
      }
    }
  } recoverWith (handleDuplicateValue)

  def getDomainDatabase(domainFqn: DomainFqn): Try[Option[DomainDatabase]] = withDb { db =>
    val DomainFqn(namespace, domainId) = domainFqn
    val params = Map(Params.Id -> domainId, Params.Namespace -> namespace)
    OrientDBUtil.findDocument(db, GetDomainQuery, params).map(_.map(docToDomainDatabase(_)))
  }

  private[this] def handleDuplicateValue[T](): PartialFunction[Throwable, Try[T]] = {
    case e: ORecordDuplicatedException =>
      e.getIndexName match {
        case DomainClass.Indices.NamespaceId =>
          Failure(DuplicateValueException(DomainClass.Indices.NamespaceId))
        case DomainClass.Indices.DatabaseName =>
          Failure(DuplicateValueException(DomainClass.Indices.DatabaseName))
        case _ =>
          Failure(e)
      }
  }
}
