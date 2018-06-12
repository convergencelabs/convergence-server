package com.convergencelabs.server.datastore.domain

import java.time.Instant
import java.util.Date

import scala.collection.JavaConverters.asScalaBufferConverter
import scala.collection.JavaConverters.mapAsJavaMapConverter
import scala.util.Try

import com.convergencelabs.server.datastore.AbstractDatabasePersistence
import com.convergencelabs.server.datastore.DatabaseProvider
import com.convergencelabs.server.datastore.QueryUtil
import com.convergencelabs.server.datastore.domain.mapper.OrientDBOperationMapper
import com.convergencelabs.server.domain.model.ModelOperation
import com.convergencelabs.server.domain.model.NewModelOperation
import com.orientechnologies.orient.core.db.document.ODatabaseDocument
import com.orientechnologies.orient.core.metadata.schema.OType
import com.orientechnologies.orient.core.record.impl.ODocument
import com.orientechnologies.orient.core.sql.OCommandSQL
import com.orientechnologies.orient.core.sql.query.OSQLSynchQuery
import com.convergencelabs.server.datastore.OrientDBUtil

object ModelOperationStore {

  import schema.ModelOperationClass._

  object Constants {
    val CollectionId = "collectionId"
    val ModelId = "modelId"
    val Username = "username"
  }

  def modelOperationToDoc(opEvent: NewModelOperation, db: ODatabaseDocument): Try[ODocument] = {
    for {
      session <- SessionStore.getDomainSessionRid(opEvent.sessionId, db)
      model <- ModelStore.getModelRid(opEvent.modelId, db)
    } yield {
      val doc: ODocument = db.newInstance(ClassName)
      doc.setProperty(Fields.Model, model, OType.LINK)
      doc.setProperty(Fields.Version, opEvent.version, OType.LONG)
      doc.setProperty(Fields.Timestamp, Date.from(opEvent.timestamp), OType.DATETIME)
      doc.setProperty(Fields.Session, session, OType.LINK)
      doc.setProperty(Fields.Operation, OrientDBOperationMapper.operationToODocument(opEvent.op))
      doc
    }
  }

  def docToModelOperation(doc: ODocument): ModelOperation = {
    val docDate: java.util.Date = doc.getProperty(Fields.Timestamp)
    val timestamp = Instant.ofEpochMilli(docDate.getTime)
    val opDoc: ODocument = doc.getProperty(Fields.Operation)
    val op = OrientDBOperationMapper.oDocumentToOperation(opDoc)

    ModelOperation(
      doc.getProperty("model.id"),
      doc.getProperty(Fields.Version),
      timestamp,
      doc.getProperty("session.user.username"),
      doc.getProperty("session.id"),
      op)
  }
}

class ModelOperationStore private[domain] (dbProvider: DatabaseProvider)
  extends AbstractDatabasePersistence(dbProvider) {

  import schema.ModelOperationClass._
  import ModelOperationStore._

  def getMaxVersion(id: String): Try[Option[Long]] = withDb { db =>
    val query = "SELECT max(version) FROM ModelOperation WHERE model.id = :modelId"
    val params = Map(Constants.ModelId -> id)
    OrientDBUtil
      .findDocument(db, query, params)
      .map(_.flatMap(doc => Option(doc.getProperty("max"))))
  }

  def getVersionAtOrBeforeTime(id: String, time: Instant): Try[Option[Long]] = withDb { db =>
    val query = "SELECT max(version) FROM ModelOperation WHERE model.id = :modelId AND timestamp <= :time"
    val params = Map(Constants.ModelId -> id, "time" -> new java.util.Date(time.toEpochMilli()))
    OrientDBUtil
      .findDocument(db, query, params)
      .map(_.flatMap(doc => Option(doc.getProperty("max"))))
  }

  def getModelOperation(id: String, version: Long): Try[Option[ModelOperation]] = withDb { db =>
    val query = "SELECT FROM ModelOperation WHERE model.id = :modelId AND version = :version"
    val params = Map(Constants.ModelId -> id, "version" -> version)
    OrientDBUtil
      .findDocument(db, query, params)
      .map(_.map(docToModelOperation(_)))
  }

  def getOperationsAfterVersion(id: String, version: Long): Try[List[ModelOperation]] = withDb { db =>
    val query =
      """SELECT * 
        |FROM ModelOperation
        |WHERE
        |  model.id = :modelId AND
        |  version >= :version
        |ORDER BY version ASC""".stripMargin
    val params = Map(Constants.ModelId -> id, Fields.Version -> version)
    OrientDBUtil
      .query(db, query, params)
      .map(_.map(docToModelOperation(_)))
  }

  def getOperationsAfterVersion(id: String, version: Long, limit: Int): Try[List[ModelOperation]] = withDb { db =>
    val queryString =
      """SELECT *
        |FROM ModelOperation
        |WHERE
        |  model.id = :modelId AND
        | version >= :version
        |ORDER BY version ASC""".stripMargin
    val query = OrientDBUtil.buildPagedQuery(queryString, Some(limit), None)
    // FIXME the limit param seems irrelevant
    val params = Map(
      Constants.ModelId -> id,
      Fields.Version -> version,
      "limit" -> limit)
    OrientDBUtil
      .query(db, query, params)
      .map(_.map(docToModelOperation(_)))
  }

  def getOperationsInVersionRange(id: String, firstVersion: Long, lastVersion: Long): Try[List[ModelOperation]] = withDb { db =>
    val query =
      s"""SELECT *
        |FROM ModelOperation
        |WHERE
        |  model.id = :modelId AND
        |  version >= :firstVersion AND
        |  version <= :lastVersion
        |ORDER BY version ASC""".stripMargin
    val params = Map(
      Constants.ModelId -> id,
      "firstVersion" -> firstVersion,
      "lastVersion" -> lastVersion)
    OrientDBUtil
      .query(db, query, params)
      .map(_.map(docToModelOperation(_)))
  }

  def deleteAllOperationsForModel(id: String): Try[Unit] = withDb { db =>
    val command = "DELETE FROM ModelOperation WHERE model.id = :modelId"
    val params = Map(Constants.ModelId -> id)
    OrientDBUtil.command(db, command, params).map(_ => ())
  }

  def deleteAllOperationsForCollection(collectionId: String): Try[Unit] = withDb { db =>
    val command = "DELETE FROM ModelOperation WHERE model.collection.id = :collectionId"
    val params = Map(Constants.CollectionId -> collectionId)
    OrientDBUtil.command(db, command, params).map(_ => ())
  }

  def createModelOperation(modelOperation: NewModelOperation): Try[Unit] = withDb { db =>
    ModelOperationStore.modelOperationToDoc(modelOperation, db).flatMap(doc => Try(doc.save()))
  }
}
