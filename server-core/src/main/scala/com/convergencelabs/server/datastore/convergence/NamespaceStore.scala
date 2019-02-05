package com.convergencelabs.server.datastore.convergence

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
import com.orientechnologies.orient.core.db.document.ODatabaseDocument
import com.orientechnologies.orient.core.id.ORID
import com.orientechnologies.orient.core.record.impl.ODocument
import com.orientechnologies.orient.core.storage.ORecordDuplicatedException

import grizzled.slf4j.Logging

object NamespaceStore {
  
  val UserNamespacePrefix = "~"

  def namespaceToDoc(namespace: Namespace, db: ODatabaseDocument): Try[ODocument] = Try {
    val Namespace(id, displayName) = namespace
    val doc = db.newInstance(NamespaceClass.ClassName).asInstanceOf[ODocument]
    doc.setProperty(Fields.Id, id)
    doc.setProperty(Fields.DisplayName, displayName)
    doc
  }

  def docToNamespace(doc: ODocument): Namespace = {
    Namespace(
      doc.getProperty(Fields.Id),
      doc.getProperty(Fields.DisplayName))
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
  
  def createNamespace(id: String, displayName: String): Try[Unit] = {
    createNamespace(Namespace(id, displayName))
  }
  
  def createUserNamespace(username: String): Try[String] = {
    val namespace = userNamespace(username)
    this.createNamespace(namespace, s"Private namespace for ${username}").map(_ => namespace)
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

  def getNamespace(id: String): Try[Option[Namespace]] = withDb { db =>
    OrientDBUtil.findDocumentFromSingleValueIndex(db, Indices.Id, id).map(_.map(docToNamespace(_)))
  }

  def getAccessibleNamespaces(username: String): Try[List[Namespace]] = withDb { db =>
    val accessQuery = """
        |SELECT
        |  expand(set(target))
        |FROM 
        |  UserRole
        |  LET $user = (SELECT @rid FROM (username = :username)
        |WHERE
        |  user = $user AND
        |  role.permissions CONTAINS (id = 'namespace-access') AND
        |  target.@class = 'Namespace'""".stripMargin
    OrientDBUtil.query(db, accessQuery, Map("username" -> username)).map(_.map(docToNamespace(_)))
  }

  def deleteNamespace(id: String): Try[Unit] = withDb { db =>
    OrientDBUtil.deleteFromSingleValueIndex(db, Indices.Id, id)
  }
  
  def deleteUserNamespace(username: String): Try[Unit] = {
    val namespace = userNamespace(username)
    this.deleteNamespace(namespace)
  }

  def updateNamespace(namespace: Namespace): Try[Unit] = withDb { db =>
    OrientDBUtil.getDocumentFromSingleValueIndex(db, Indices.Id, namespace.id)
      .flatMap { existing =>
        namespaceToDoc(namespace, db).map { updated =>
          existing.merge(updated, true, false)
          db.save(existing)
          ()
        }
      }
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
