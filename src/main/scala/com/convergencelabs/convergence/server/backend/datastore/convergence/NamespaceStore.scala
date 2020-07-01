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

import com.convergencelabs.convergence.server.backend.datastore.convergence.schema.NamespaceClass
import com.convergencelabs.convergence.server.backend.datastore.convergence.schema.NamespaceClass.{Fields, Indices}
import com.convergencelabs.convergence.server.backend.datastore.{AbstractDatabasePersistence, DuplicateValueException, OrientDBUtil}
import com.convergencelabs.convergence.server.db.DatabaseProvider
import com.convergencelabs.convergence.server.domain.Domain
import com.convergencelabs.convergence.server.model.domain.Namespace
import com.convergencelabs.convergence.server.util.{QueryLimit, QueryOffset}
import com.orientechnologies.orient.core.db.document.ODatabaseDocument
import com.orientechnologies.orient.core.id.ORID
import com.orientechnologies.orient.core.record.impl.ODocument
import com.orientechnologies.orient.core.storage.ORecordDuplicatedException
import grizzled.slf4j.Logging

import scala.jdk.CollectionConverters._
import scala.util.{Failure, Try}


class NamespaceStore(dbProvider: DatabaseProvider) extends AbstractDatabasePersistence(dbProvider) with Logging {

  import NamespaceStore._

  def createNamespace(id: String, displayName: String, userNamespace: Boolean): Try[Unit] = {
    createNamespace(Namespace(id, displayName, userNamespace))
  }

  def createUserNamespace(username: String): Try[String] = {
    val namespace = userNamespace(username)
    this.createNamespace(namespace, namespace, userNamespace = true).map(_ => namespace)
  }

  def createNamespace(namespace: Namespace): Try[Unit] = withDb { db =>
    for {
      doc <- NamespaceStore.namespaceToDoc(namespace, db)
      _ <- Try(db.save(doc).asInstanceOf[ODocument])
    } yield ()
  } recoverWith handleDuplicateValue

  def namespaceExists(id: String): Try[Boolean] = withDb { db =>
    OrientDBUtil.indexContains(db, Indices.Id, id)
  }
  
  private[this] val NamespaceCountQuery = "SELECT count(@rid) as count FROM Namespace WHERE NOT(id LIKE '~%')"
  def namespaceCount(): Try[Long] = withDb { db =>
    OrientDBUtil.getDocument(db, NamespaceCountQuery).map(_.getProperty("count").asInstanceOf[Long])
  }

  def getNamespace(id: String): Try[Option[Namespace]] = withDb { db =>
    OrientDBUtil.findDocumentFromSingleValueIndex(db, Indices.Id, id).map(_.map(docToNamespace))
  }

  private[this] val GetNamespacesQuery = "SELECT FROM Namespace ORDER BY id ASC"
  def getNamespaces(offset: QueryOffset, limit: QueryLimit): Try[List[Namespace]] = withDb { db =>
    val query = OrientDBUtil.buildPagedQuery(GetNamespacesQuery, limit, offset)
    OrientDBUtil.queryAndMap(db, query)(docToNamespace)
  }

  def getAccessibleNamespaces(username: String): Try[List[Namespace]] = withDb { db =>
    val accessQuery = """
        |SELECT
        |  expand(set(target))
        |FROM 
        |  UserRole
        |WHERE
        |  user IN (SELECT FROM User WHERE username = :username) AND
        |  role.permissions CONTAINS (id = 'namespace-access') AND
        |  target.@class = 'Namespace'""".stripMargin
    OrientDBUtil.query(db, accessQuery, Map(Params.Username -> username)).map(_.map(docToNamespace))
  }

  def getNamespaceAndDomains(namespaces: Set[String], offset: QueryOffset, limit: QueryLimit): Try[Set[NamespaceAndDomains]] = withDb { db =>
    val baseQuery = "SELECT FROM Namespace WHERE id IN :ids AND userNamespace = false ORDER BY id ASC"
    val query = OrientDBUtil.buildPagedQuery(baseQuery, limit, offset)
    val params = Map("ids" -> namespaces)
    OrientDBUtil.query(db, query, params).flatMap(getNamespaceAndDomainsFromDocs(_, db))
  }

  def getAllNamespacesAndDomains(offset: QueryOffset, limit: QueryLimit): Try[Set[NamespaceAndDomains]] = withDb { db =>
    val baseQuery = "SELECT FROM Namespace WHERE userNamespace = false ORDER BY id ASC"
    val query = OrientDBUtil.buildPagedQuery(baseQuery, limit, offset)
    OrientDBUtil.query(db, query).flatMap(getNamespaceAndDomainsFromDocs(_, db))
  }

  private[this] def getNamespaceAndDomainsFromDocs(docs: List[ODocument], db: ODatabaseDocument): Try[Set[NamespaceAndDomains]] = {
    val namespaces = docs.map(docToNamespace)
    val namespaceRids = docs.map(_.getIdentity)
    val query = "SELECT FROM Domain WHERE namespace IN :namespaces"
    val params = Map("namespaces" -> namespaceRids.asJava)
    val domains = OrientDBUtil.queryAndMap(db, query, params)(DomainStore.docToDomain)
    domains.map(_.groupBy(_.domainId.namespace)).map { domainsByNamespace =>
      val namespaceAndDomains = namespaces.map { namespace =>
        val domains = domainsByNamespace.get(namespace.id).map(_.toSet).getOrElse(Set())
        NamespaceAndDomains(namespace.id, namespace.displayName, domains)
      }
      namespaceAndDomains.toSet
    }
  }

  def deleteNamespace(id: String): Try[Unit] = withDb { db =>
    OrientDBUtil.deleteFromSingleValueIndex(db, Indices.Id, id)
  }

  def deleteUserNamespace(username: String): Try[Unit] = {
    val namespace = userNamespace(username)
    this.deleteNamespace(namespace)
  }

  def updateNamespace(namespace: NamespaceUpdates): Try[Unit] = withDb { db =>
    val command = "UPDATE Namespace SET displayName = :displayName WHERE id = :id"
    val params = Map(Params.Id -> namespace.id, Params.DisplayName -> namespace.displayName)
    OrientDBUtil.mutateOneDocument(db, command, params).map(_ => ())
  } recoverWith handleDuplicateValue

  private[this] def handleDuplicateValue[T]: PartialFunction[Throwable, Try[T]] = {
    case e: ORecordDuplicatedException =>
      e.getIndexName match {
        case Indices.Id =>
          Failure(DuplicateValueException(Fields.Id))
        case Indices.DisplayName =>
          Failure(DuplicateValueException(Fields.DisplayName))
        case _ =>
          Failure(e)
      }
  }
}


object NamespaceStore {

  case class NamespaceUpdates(id: String, displayName: String)
  case class NamespaceAndDomains(id: String, displayName: String, domains: Set[Domain])

  val UserNamespacePrefix = "~"

  object Params {
    val Username = "username"
    val Id = "id"
    val DisplayName = "displayName"
  }

  def namespaceToDoc(namespace: Namespace, db: ODatabaseDocument): Try[ODocument] = Try {
    val Namespace(id, displayName, userNamespace) = namespace
    val doc = db.newInstance(NamespaceClass.ClassName).asInstanceOf[ODocument]
    doc.setProperty(Fields.Id, id)
    doc.setProperty(Fields.DisplayName, displayName)
    doc.setProperty(Fields.UserNamespace, userNamespace)
    doc
  }

  def docToNamespace(doc: ODocument): Namespace = {
    Namespace(
      doc.getProperty(Fields.Id),
      doc.getProperty(Fields.DisplayName),
      doc.getProperty(Fields.UserNamespace))
  }

  def getNamespaceRid(id: String, db: ODatabaseDocument): Try[ORID] = {
    OrientDBUtil.getIdentityFromSingleValueIndex(db, Indices.Id, id)
  }

  def userNamespace(username: String): String = {
    UserNamespacePrefix + username
  }
}
