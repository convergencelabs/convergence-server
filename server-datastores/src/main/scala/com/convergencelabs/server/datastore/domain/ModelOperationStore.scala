package com.convergencelabs.server.datastore.domain

import java.time.Instant
import java.util.Date

import scala.collection.JavaConverters.asScalaBufferConverter
import scala.collection.JavaConverters.mapAsJavaMapConverter
import scala.util.Try

import org.json4s.NoTypeHints
import org.json4s.jackson.Serialization

import com.convergencelabs.server.datastore.AbstractDatabasePersistence
import com.convergencelabs.server.datastore.DatabaseProvider
import com.convergencelabs.server.datastore.QueryUtil
import com.convergencelabs.server.datastore.domain.mapper.OrientDBOperationMapper
import com.convergencelabs.server.domain.model.ModelFqn
import com.convergencelabs.server.domain.model.ModelOperation
import com.convergencelabs.server.domain.model.NewModelOperation
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx
import com.orientechnologies.orient.core.metadata.schema.OType
import com.orientechnologies.orient.core.record.impl.ODocument
import com.orientechnologies.orient.core.sql.OCommandSQL
import com.orientechnologies.orient.core.sql.query.OSQLSynchQuery

import ModelOperationStore.Constants.CollectionId
import ModelOperationStore.Constants.ModelId
import ModelOperationStore.Fields.Model
import ModelOperationStore.Fields.Operation
import ModelOperationStore.Fields.Session
import ModelOperationStore.Fields.Timestamp
import ModelOperationStore.Fields.Version

object ModelOperationStore {
  val ClassName = "ModelOperation"
  
  object Fields {
    val Model = "model"
    val Version = "version"
    val Timestamp = "timestamp"
    val User = "user"
    val Session = "session"
    val Operation = "operation"
  }

  object Constants {
    val CollectionId = "collectionId"
    val ModelId = "modelId"
    val Username = "username"
  }
  
  def modelOperationToDoc(opEvent: NewModelOperation, db: ODatabaseDocumentTx): Try[ODocument] = {
    for {
      session <- SessionStore.getDomainSessionRid(opEvent.sessionId, db)
      model <- ModelStore.getModelRid(opEvent.modelFqn.modelId, opEvent.modelFqn.collectionId, db)
    } yield {
      val doc = db.newInstance(ModelOperationStore.ClassName)
      doc.field(Model, model, OType.LINK)
      doc.field(Version, opEvent.version, OType.LONG)
      doc.field(Timestamp, Date.from(opEvent.timestamp), OType.DATETIME)
      doc.field(Session, session, OType.LINK)
      doc.field(Operation, OrientDBOperationMapper.operationToODocument(opEvent.op))
      doc
    }
  }

  def docToModelOperation(doc: ODocument): ModelOperation = {
    val docDate: java.util.Date = doc.field(Timestamp, OType.DATETIME)
    val timestamp = Instant.ofEpochMilli(docDate.getTime)
    val opDoc: ODocument = doc.field(Operation, OType.EMBEDDED)
    val op = OrientDBOperationMapper.oDocumentToOperation(opDoc)

    ModelOperation(
      ModelFqn(doc.field("model.collection.id"), doc.field("model.id")),
      doc.field(Version),
      timestamp,
      doc.field("session.user.username"),
      doc.field("session.id"),
      op)
  }
}

class ModelOperationStore private[domain] (dbProvider: DatabaseProvider)
    extends AbstractDatabasePersistence(dbProvider) {

  private[this] implicit val formats = Serialization.formats(NoTypeHints)

  def getMaxVersion(fqn: ModelFqn): Try[Option[Long]] = tryWithDb { db =>
    val queryString =
      """SELECT max(version)
        |FROM ModelOperation
        |WHERE
        |  model.collection.id = :collectionId AND
        |  model.id = :modelId""".stripMargin

    val query = new OSQLSynchQuery[ODocument](queryString)
    val params = Map(CollectionId -> fqn.collectionId, ModelId -> fqn.modelId)
    val result: java.util.List[ODocument] = db.command(query).execute(params.asJava)

    result.asScala.toList match {
      case doc :: Nil => Some(doc.field("max", OType.LONG))
      case _ => None
    }
  }

  def getVersionAtOrBeforeTime(fqn: ModelFqn, time: Instant): Try[Option[Long]] = tryWithDb { db =>
    val queryString =
      """SELECT max(version)
        |FROM ModelOperation
        |WHERE
        |  model.collection.id = :collectionId AND
        |  model.id = :modelId AND
        |  timestamp <= :time""".stripMargin

    val query = new OSQLSynchQuery[ODocument](queryString)
    val params = Map(CollectionId -> fqn.collectionId, ModelId -> fqn.modelId, "time" -> new java.util.Date(time.toEpochMilli()))
    val result: java.util.List[ODocument] = db.command(query).execute(params.asJava)
    result.asScala.toList match {
      case doc :: rest => Some(doc.field("max", OType.LONG))
      case Nil => None
    }
  }
  
  def getModelOperation(fqn: ModelFqn, version: Long): Try[Option[ModelOperation]] = tryWithDb { db =>
    val query =
      """SELECT *
        |FROM ModelOperation
        |WHERE
        |  model.collection.id = :collectionId AND
        |  model.id = :modelId AND
        |  version = :version""".stripMargin
    val params = Map(CollectionId -> fqn.collectionId, ModelId -> fqn.modelId, "version" -> version)
    QueryUtil.lookupOptionalDocument(query, params, db) map {ModelOperationStore.docToModelOperation(_)}
  }

  def getOperationsAfterVersion(fqn: ModelFqn, version: Long): Try[List[ModelOperation]] = tryWithDb { db =>
    val queryString =
      """SELECT * 
        |FROM ModelOperation
        |WHERE
        |  model.collection.id = :collectionId AND
        |  model.id = :modelId AND
        |  version >= :version
        |ORDER BY version ASC""".stripMargin

    val query = new OSQLSynchQuery[ODocument](queryString)
    val params = Map(CollectionId -> fqn.collectionId, ModelId -> fqn.modelId, "version" -> version)
    val result: java.util.List[ODocument] = db.command(query).execute(params.asJava)
    result.asScala.toList map { ModelOperationStore.docToModelOperation(_) }
  }

  def getOperationsAfterVersion(fqn: ModelFqn, version: Long, limit: Int): Try[List[ModelOperation]] = tryWithDb { db =>
    val queryString =
      """SELECT *
        |FROM ModelOperation
        |WHERE
        |  model.collection.id = :collectionId AND
        |  model.id = :modelId AND
        | version >= :version
        |ORDER BY version ASC""".stripMargin

    val query = new OSQLSynchQuery[ODocument](QueryUtil.buildPagedQuery(queryString, Some(limit), None))
    val params = Map(
      CollectionId -> fqn.collectionId,
      ModelId -> fqn.modelId,
      "version" -> version,
      "limit" -> limit)
    val result: java.util.List[ODocument] = db.command(query).execute(params.asJava)
    result.asScala.toList map { ModelOperationStore.docToModelOperation(_) }
  }

  def getOperationsInVersionRange(fqn: ModelFqn, firstVersion: Long, lastVersion: Long): Try[List[ModelOperation]] = tryWithDb { db =>
    val queryString =
      s"""SELECT *
        |FROM ModelOperation
        |WHERE
        |  model.collection.id = :collectionId AND
        |  model.id = :modelId AND
        |  version >= :firstVersion AND
        |  version <= :lastVersion
        |ORDER BY version ASC""".stripMargin

    val query = new OSQLSynchQuery[ODocument](queryString)
    val params = Map(
      CollectionId -> fqn.collectionId,
      ModelId -> fqn.modelId,
      "firstVersion" -> firstVersion,
      "lastVersion" -> lastVersion)
    val result: java.util.List[ODocument] = db.command(query).execute(params.asJava)
    result.asScala.toList map { ModelOperationStore.docToModelOperation(_) }
  }

  def deleteAllOperationsForModel(fqn: ModelFqn): Try[Unit] = tryWithDb { db =>
    val commandString =
      """DELETE FROM ModelOperation
        |WHERE
        |  model.collection.id = :collectionId AND
        |  model.id = :modelId""".stripMargin

    val params = Map(CollectionId -> fqn.collectionId, ModelId -> fqn.modelId)
    val command = new OCommandSQL(commandString)
    db.command(command).execute(params.asJava)
    ()
  }

  def deleteAllOperationsForCollection(collectionId: String): Try[Unit] = tryWithDb { db =>
    val commandString =
      """DELETE FROM ModelOperation
        |WHERE
        |  model.collection.id = :collectionId""".stripMargin

    val params = Map(CollectionId -> collectionId)
    val command = new OCommandSQL(commandString)
    db.command(command).execute(params.asJava)
    ()
  }

  def createModelOperation(modelOperation: NewModelOperation): Try[Unit] = tryWithDb { db =>
    val doc = ModelOperationStore.modelOperationToDoc(modelOperation, db).get
    db.save(doc)
    ()
  }
}
