/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is part of the Convergence Server, which is released under
 * the terms of the GNU General Public License version 3 (GPLv3). A copy
 * of the GPLv3 should have been provided along with this file, typically
 * located in the "LICENSE" file, which is part of this source code package.
 * Alternatively, see <https://www.gnu.org/licenses/gpl-3.0.html> for the
 * full text of the GPLv3 license, if it was not provided.
 */

package com.convergencelabs.convergence.server.backend.datastore.convergence

import com.convergencelabs.convergence.server.backend.datastore.convergence.schema.DomainClass
import com.convergencelabs.convergence.server.backend.datastore.{AbstractDatabasePersistence, DuplicateValueException, EntityNotFoundException, OrientDBUtil}
import com.convergencelabs.convergence.server.backend.db.DatabaseProvider
import com.convergencelabs.convergence.server.model.server.domain.{Domain, DomainDatabase, DomainStatus}
import com.convergencelabs.convergence.server.model.{DomainId, server}
import com.convergencelabs.convergence.server.util.{QueryLimit, QueryOffset}
import com.orientechnologies.orient.core.db.document.ODatabaseDocument
import com.orientechnologies.orient.core.id.ORID
import com.orientechnologies.orient.core.record.impl.ODocument
import com.orientechnologies.orient.core.storage.ORecordDuplicatedException
import grizzled.slf4j.Logging

import scala.util.{Failure, Try}


class DomainStore(dbProvider: DatabaseProvider)
  extends AbstractDatabasePersistence(dbProvider)
    with Logging {

  import DomainStore._

  def createDomain(domainId: DomainId, displayName: String, domainDatabase: DomainDatabase): Try[Unit] = withDb { db =>
    val domain = server.domain.Domain(domainId, displayName, DomainStatus.Initializing, "")
    domainToDoc(domain, db).map { doc =>
      addDomainDatabaseFields(doc, domainDatabase)
      db.save(doc)
      ()
    }
  } recoverWith handleDuplicateValue

  private[this] val DomainExistsQuery = "SELECT count(@rid) as count FROM Domain WHERE id = :id AND namespace.id = :namespace"

  def domainExists(domainId: DomainId): Try[Boolean] = withDb { db =>
    val DomainId(namespace, id) = domainId
    val params = Map(Params.Id -> id, Params.Namespace -> namespace)
    OrientDBUtil.getDocument(db, DomainExistsQuery, params).map(_.getProperty("count").asInstanceOf[Long] > 0)
  }

  private[this] val DomainCountQuery = "SELECT count(@rid) as count FROM Domain"

  def domainCount(): Try[Long] = withDb { db =>
    OrientDBUtil.getDocument(db, DomainCountQuery).map(_.getProperty("count").asInstanceOf[Long])
  }

  private[this] val GetDomainQuery = "SELECT FROM Domain WHERE namespace.id = :namespace AND id = :id"

  def getDomainByFqn(domainId: DomainId): Try[Option[Domain]] = withDb { db =>
    val DomainId(namespace, id) = domainId
    val params = Map(Params.Id -> id, Params.Namespace -> namespace)
    OrientDBUtil.findDocument(db, GetDomainQuery, params).map(_.map(docToDomain))
  }

  private[this] val GetDomainsByNamespaceQuery = "SELECT FROM Domain WHERE namespace.id = :namespace"

  def getDomainsInNamespace(namespace: String): Try[List[Domain]] = withDb { db =>
    val params = Map(Params.Namespace -> namespace)
    OrientDBUtil.query(db, GetDomainsByNamespaceQuery, params).map(_.map(docToDomain))
  }

  def getDomains(namespace: Option[String], filter: Option[String], offset: QueryOffset, limit: QueryLimit): Try[List[Domain]] = withDb { db =>
    val baseQuery = "SELECT FROM Domain"
    val (filterWhere, filterParams) = filter.map(filter => {
      val where = " (id.toLowerCase() LIKE :filter OR displayName.toLowerCase() LIKE :filter)"
      val params = Map[String, Any](Params.Filter -> s"%$filter%")
      (where, params)
    }).getOrElse("", Map[String, Any]())

    val (namespaceWhere, namespaceParams) = namespace.map(ns => {
      val where = " namespace.id = :namespace"
      val params = Map[String, Any](Params.Namespace -> ns)
      (where, params)
    }).getOrElse("", Map[String, Any]())

    val whereClause = (namespace, filter) match {
      case (Some(_), Some(_)) =>
        " WHERE" + namespaceWhere + " AND" + filterWhere
      case (Some(_), None) =>
        " WHERE" + namespaceWhere
      case (None, Some(_)) =>
        " WHERE" + filterWhere
      case _ =>
        ""
    }

    val params = filterParams ++ namespaceParams
    val query = OrientDBUtil.buildPagedQuery(baseQuery + whereClause, limit, offset)
    OrientDBUtil.query(db, query, params).map(_.map(DomainStore.docToDomain))
  }

  def getDomainsByAccess(username: String, namespace: Option[String], filter: Option[String], offset: QueryOffset, limit: QueryLimit): Try[List[Domain]] = withDb { db =>
    val accessQuery =
      """LET namespaces = SELECT set(target) FROM UserRole WHERE user.username = :username AND (role.permissions CONTAINS ('namespace-access') AND target.@class = 'Namespace');
        |LET domainsInNamespaces = SELECT FROM Domain WHERE namespace IN $namespaces;
        |LET roleAccess = SELECT expand(set(target)) FROM UserRole WHERE user.username = :username AND role.permissions CONTAINS ('domain-access') AND target.@class = 'Domain';
        |LET allDomains = SELECT expand(set(unionall($domainsInNamespaces, $roleAccess))) as domains;
        |SELECT * FROM $allDomains WHERE true""".stripMargin

    val (filterWhere, filterParams) = filter.map(filter => {
      val where = " AND (id.toLowerCase() LIKE :filter OR displayName.toLowerCase() LIKE :filter)"
      val params = Map[String, String](Params.Filter -> s"%$filter%")
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
    OrientDBUtil.execute(db, query, params).map(_.map(DomainStore.docToDomain))
  }

  private[this] val DeleteDomainCommand = "DELETE FROM Domain WHERE namespace.id = :namespace AND id = :id"

  def removeDomain(domainId: DomainId): Try[Unit] = withDb { db =>
    val DomainId(namespace, id) = domainId
    val params = Map(Params.Id -> id, Params.Namespace -> namespace)
    OrientDBUtil.mutateOneDocument(db, DeleteDomainCommand, params)
  }

  def updateDomain(domain: Domain): Try[Unit] = withDb { db =>
    val params = Map(Params.Namespace -> domain.domainId.namespace, Params.Id -> domain.domainId.domainId)
    OrientDBUtil.getDocument(db, GetDomainQuery, params).flatMap { existing =>
      DomainStore.domainToDoc(domain, db).map { updated =>
        existing.merge(updated, true, false)
        db.save(existing)
        ()
      }
    }
  } recoverWith handleDuplicateValue


  /**
   * Updates the status and status message of a specific domain.
   *
   * @param domainId      The id of the domain to set the status for.
   * @param status        The status flag for the domain.
   * @param statusMessage The status message for the domain.
   * @return Success if updating the domain status succeeds, a Failure
   *         otherwise.
   */
  def updateDomainStatus(domainId: DomainId, status: DomainStatus.Value, statusMessage: String): Try[Unit] = withDb { db =>
    val params = Map(
      Params.Namespace -> domainId.namespace,
      Params.Id -> domainId.domainId,
      "status" -> status.toString,
      "statusMessage" -> statusMessage
    )
    OrientDBUtil.mutateOneDocument(db, UpdateDomainStatusCommand, params)
  }

  private[this] val UpdateDomainStatusCommand = "UPDATE Domain SET status = :status, statusMessage = :statusMessage WHERE namespace.id = :namespace AND id = :id"


  def setDomainStatus(domain: DomainId, status: DomainStatus.Value, statusMessage: String): Try[Unit] = withDb { db =>
    val params = Map(
      Params.Namespace -> domain.namespace,
      Params.Id -> domain.domainId,
      Params.Status -> status.toString.toLowerCase,
      Params.StatusMessage -> statusMessage)
    OrientDBUtil.mutateOneDocument(db, SetDomainStatusCommand, params)
  }

  private[this] val SetDomainStatusCommand = "UPDATE Domain SET status = :status, statusMessage = :statusMessage WHERE namespace.id = :namespace AND id = :id"


  def getDomainDatabase(domainId: DomainId): Try[Option[DomainDatabase]] = withDb { db =>
    val DomainId(namespace, id) = domainId
    val params = Map(Params.Id -> id, Params.Namespace -> namespace)
    OrientDBUtil.findDocument(db, GetDomainQuery, params).map(_.map(docToDomainDatabase))
  }

  private[this] def handleDuplicateValue[T]: PartialFunction[Throwable, Try[T]] = {
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


object DomainStore {

  object Params {
    val Namespace = "namespace"
    val Id = "id"
    val DisplayName = "displayName"
    val Status = "status"
    val StatusMessage = "statusMessage"
    val Filter = "filter"
    val Username = "username"
  }

  def domainToDoc(domain: Domain, db: ODatabaseDocument): Try[ODocument] = {
    val Domain(DomainId(namespace, id), displayName, status, statusMessage) = domain
    NamespaceStore.getNamespaceRid(namespace, db).map { nsRid =>
      val doc = db.newInstance(DomainClass.ClassName).asInstanceOf[ODocument]
      doc.setProperty(DomainClass.Fields.Id, id)
      doc.setProperty(DomainClass.Fields.Namespace, nsRid)
      doc.setProperty(DomainClass.Fields.DisplayName, displayName)
      doc.setProperty(DomainClass.Fields.Status, status.toString)
      doc.setProperty(DomainClass.Fields.StatusMessage, statusMessage)
      doc
    }.recoverWith {
      case _: EntityNotFoundException =>
        Failure(NamespaceNotFoundException(namespace))
    }
  }

  def docToDomain(doc: ODocument): Domain = {
    val status: DomainStatus.Value = DomainStatus.withName(doc.field(DomainClass.Fields.Status))
    val namespace = doc.eval("namespace.id").asInstanceOf[String]
    val fqn = DomainId(namespace, doc.getProperty(DomainClass.Fields.Id))
    val displayName = doc.getProperty(DomainClass.Fields.DisplayName).asInstanceOf[String]
    val statusMessage = doc.field(DomainClass.Fields.StatusMessage).asInstanceOf[String]
    Domain(fqn, displayName, status, statusMessage)
  }

  def getDomainRid(domainId: DomainId, db: ODatabaseDocument): Try[ORID] = {
    getDomainRid(domainId.namespace, domainId.domainId, db)
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
