/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

package com.convergencelabs.server.datastore.convergence
import scala.collection.JavaConverters.seqAsJavaListConverter
import scala.util.Failure
import scala.util.Try

import com.convergencelabs.server.datastore.AbstractDatabasePersistence
import com.convergencelabs.server.datastore.DuplicateValueException
import com.convergencelabs.server.datastore.OrientDBUtil
import com.convergencelabs.server.datastore.convergence.schema.NamespaceClass
import com.convergencelabs.server.datastore.convergence.schema.NamespaceClass.Fields
import com.convergencelabs.server.datastore.convergence.schema.NamespaceClass.Indices
import com.convergencelabs.server.db.DatabaseProvider
import com.convergencelabs.server.domain.Namespace
import com.convergencelabs.server.domain.NamespaceAndDomains
import com.orientechnologies.orient.core.db.document.ODatabaseDocument
import com.orientechnologies.orient.core.id.ORID
import com.orientechnologies.orient.core.record.impl.ODocument
import com.orientechnologies.orient.core.storage.ORecordDuplicatedException

import grizzled.slf4j.Logging
import com.convergencelabs.server.domain.NamespaceUpdates

object NamespaceStore {

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

class NamespaceStore(dbProvider: DatabaseProvider)
  extends AbstractDatabasePersistence(dbProvider)
  with Logging {

  import NamespaceStore._

  def createNamespace(id: String, displayName: String, userNamespace: Boolean): Try[Unit] = {
    createNamespace(Namespace(id, displayName, userNamespace))
  }

  def createUserNamespace(username: String): Try[String] = {
    val namespace = userNamespace(username)
    this.createNamespace(namespace, namespace, true).map(_ => namespace)
  }

  def createNamespace(namespace: Namespace): Try[Unit] = withDb { db =>
    NamespaceStore.namespaceToDoc(namespace, db).map { doc =>
      db.save(doc)
      ()
    }
  } recoverWith (handleDuplicateValue)

  def namespaceExists(id: String): Try[Boolean] = withDb { db =>
    OrientDBUtil.indexContains(db, Indices.Id, id)
  }
  
  private[this] val NamespaceCountQuery = "SELECT count(@rid) as count FROM Namespace WHERE NOT(id LIKE '~%')"
  def namespaceCount(): Try[Long] = withDb { db =>
    OrientDBUtil.getDocument(db, NamespaceCountQuery).map(_.getProperty("count").asInstanceOf[Long])
  }

  def getNamespace(id: String): Try[Option[Namespace]] = withDb { db =>
    OrientDBUtil.findDocumentFromSingleValueIndex(db, Indices.Id, id).map(_.map(docToNamespace(_)))
  }

  private[this] val GetNamespacesQuery = "SELECT FROM Namespace"
  def getNamespaces(): Try[List[Namespace]] = withDb { db =>
    OrientDBUtil.queryAndMap(db, GetNamespacesQuery)(docToNamespace(_))
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
    OrientDBUtil.query(db, accessQuery, Map(Params.Username -> username)).map(_.map(docToNamespace(_)))
  }

  def getNamespaceAndDomains(namespaces: Set[String]): Try[Set[NamespaceAndDomains]] = withDb { db =>
    val query = "SELECT FROM Namespace WHERE id IN :ids AND userNamespace = false"
    val params = Map("ids" -> namespaces)
    OrientDBUtil.query(db, query, params).flatMap(getNamespaceAndDomainsFromDocs(_, db))
  }

  def getAllNamespacesAndDomains(): Try[Set[NamespaceAndDomains]] = withDb { db =>
    val query = "SELECT FROM Namespace WHERE userNamespace = false"
    OrientDBUtil.query(db, query).flatMap(getNamespaceAndDomainsFromDocs(_, db))
  }

  private[this] def getNamespaceAndDomainsFromDocs(docs: List[ODocument], db: ODatabaseDocument): Try[Set[NamespaceAndDomains]] = {
    val namespaces = docs.map(docToNamespace(_))
    val namespaceRids = docs.map(_.getIdentity)
    val query = "SELECT FROM Domain WHERE namespace IN :namespaces"
    val params = Map("namespaces" -> namespaceRids.asJava)
    val domains = OrientDBUtil.queryAndMap(db, query, params)(DomainStore.docToDomain(_))
    domains.map(_.groupBy(_.domainFqn.namespace)).map { domainsByNamespace =>
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
  } recoverWith (handleDuplicateValue)

  private[this] def handleDuplicateValue[T](): PartialFunction[Throwable, Try[T]] = {
    case e: ORecordDuplicatedException =>
      e.getIndexName match {
        case Indices.Id =>
          Failure(DuplicateValueException("id"))
        case Indices.DisplayName =>
          Failure(DuplicateValueException("displayName"))
        case _ =>
          Failure(e)
      }
  }
}
