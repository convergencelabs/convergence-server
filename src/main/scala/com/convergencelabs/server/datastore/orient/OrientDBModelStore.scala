package com.convergencelabs.server.datastore.orient

import scala.collection.JavaConversions._
import scala.collection.JavaConverters._
import org.json4s.JsonAST.JValue
import com.convergencelabs.server.datastore.domain.DataType
import com.convergencelabs.server.datastore.domain.ModelData
import com.convergencelabs.server.datastore.domain.ModelMetaData
import com.convergencelabs.server.datastore.domain.ModelStore
import com.convergencelabs.server.domain.model.ModelFqn
import com.convergencelabs.server.domain.model.ot.ops.Operation
import com.orientechnologies.orient.core.db.OPartitionedDatabasePool
import com.orientechnologies.orient.core.record.impl.ODocument
import com.orientechnologies.orient.core.sql.query.OResultSet
import com.orientechnologies.orient.core.sql.query.OSQLSynchQuery
import org.json4s.NoTypeHints
import com.fasterxml.jackson.databind.node.ObjectNode
import org.json4s.jackson.Serialization
import org.json4s._
import org.json4s.jackson.JsonMethods._
import com.convergencelabs.server.domain.model.ot.ops.StringInsertOperation
import com.convergencelabs.server.domain.model.ot.ops.CompoundOperation
import com.orientechnologies.orient.core.sql.OCommandSQL
import com.convergencelabs.server.datastore.domain.ModelMetaData
import com.orientechnologies.orient.core.metadata.schema.OType
import com.convergencelabs.server.datastore.domain.ModelMetaData
import org.json4s.JsonAST.JNumber
import scala.collection.immutable.HashMap

object OrientDBModelStore {
  def toOrientPath(path: List[Any]): String = {
    ???
  }
}

class OrientDBModelStore(dbPool: OPartitionedDatabasePool) extends ModelStore {

  private[this] implicit val formats = Serialization.formats(NoTypeHints)

  def modelExists(fqn: ModelFqn): Boolean = {
    val db = dbPool.acquire()
    val query = new OSQLSynchQuery[ODocument]("SELECT modelId FROM model WHERE collectionId = :collectionId and modelId = :modelId")
    val params: java.util.Map[String, String] = HashMap("collectionId" -> fqn.collectionId, "modelId" -> fqn.modelId)
    val result: java.util.List[ODocument] = db.command(query).execute(params)
    db.close()
    !result.isEmpty()
  }

  def createModel(fqn: ModelFqn, data: JValue, creationTime: Long): Unit = {
    val db = dbPool.acquire()
    val dataObject = JObject(List(("data", data)))
    val doc = db.newInstance("model")
    doc.fromJSON(compact(render(dataObject)))
    doc.field("modelId", fqn.modelId)
    doc.field("collectionId", fqn.collectionId)
    doc.field("version", 0)
    doc.field("creationDate", creationTime)
    doc.field("modifiedDate", creationTime)
    db.save(doc)
    db.close()
  }

  def deleteModel(fqn: ModelFqn): Unit = {
    val db = dbPool.acquire()
    val command = new OCommandSQL("DELETE FROM model WHERE collectionId = :collectionId AND modelId = :modelId")
    val params = Map("collectionId" -> fqn.collectionId, "modelId" -> fqn.modelId)
    db.command(command).execute(params);
  }

  def getModelMetaData(fqn: ModelFqn): Option[ModelMetaData] = {
    val db = dbPool.acquire()
    val query = new OSQLSynchQuery[ODocument]("SELECT modelId, collectionId, version, created, modified FROM model WHERE collectionId = :collectionId AND modelId = :modelId")
    val params: java.util.Map[String, String] = HashMap("collectionId" -> fqn.collectionId, "modelId" -> fqn.modelId)

    val result: java.util.List[ODocument] = db.command(query).execute(params)
    result.asScala.toList match {
      case doc :: rest => Some(ModelMetaData(fqn, doc.field("version", OType.LONG), doc.field("created", OType.LONG), doc.field("modified", OType.LONG)))
      case Nil         => None
    }
  }

  def getModelData(fqn: ModelFqn): Option[ModelData] = {
    val db = dbPool.acquire()
    val query = new OSQLSynchQuery[ODocument]("SELECT FROM model WHERE collectionId = :collectionId and modelId = :modelId")
    val params: java.util.Map[String, String] = HashMap("collectionId" -> fqn.collectionId, "modelId" -> fqn.modelId)
    val result: java.util.List[ODocument] = db.command(query).execute(params)
    result.asScala.toList match {
      case doc :: rest => Some(ModelData(ModelMetaData(fqn, doc.field("version", OType.LONG), doc.field("created", OType.LONG), doc.field("modified", OType.LONG)), parse(doc.field("data"))))
      case Nil         => None
    }
  }

  def getModelJsonData(fqn: ModelFqn): Option[JValue] = {
    val db = dbPool.acquire()
    val query = new OSQLSynchQuery[ODocument]("SELECT data FROM model WHERE collectionId = :collectionId and modelId = :modelId")
    val params: java.util.Map[String, String] = HashMap("collectionId" -> fqn.collectionId, "modelId" -> fqn.modelId)
    val result: java.util.List[ODocument] = db.command(query).execute(params)
    result.asScala.toList match {
      case doc :: rest => Some(parse(doc.field("data")))
      case Nil         => None
    }
  }

  def applyOperationToModel(fqn: ModelFqn, operation: Operation, version: Long, timestamp: Long, username: String): Unit = {
    operation match {
      case compoundOp: CompoundOperation => compoundOp.operations foreach { op => applyOperationToModel(fqn, op, version, timestamp, username) }
      case _                            => ???
    }
  }

  def getModelFieldDataType(fqn: ModelFqn, path: List[Any]): Option[DataType.Value] = {
    val db = dbPool.acquire()
    val pathString = OrientDBModelStore.toOrientPath(path)
    val query = new OSQLSynchQuery[ODocument](s"SELECT pathString FROM model WHERE collectionId = :collectionId and modelId = :modelId")
    val params: java.util.Map[String, String] = HashMap("collectionId" -> fqn.collectionId, "modelId" -> fqn.modelId)
    val result: java.util.List[ODocument] = db.command(query).execute(params)
    result.asScala.toList match {
      case doc :: rest => {
        parse(doc.field("data")) match {
          case data: JObject => Some(DataType.OBJECT)
          case data: JArray  => Some(DataType.ARRAY)
          case data: JString => Some(DataType.STRING)
          case data: JNumber => Some(DataType.NUMBER)
          case data: JBool   => Some(DataType.BOOLEAN)
          case _             => Some(DataType.NULL)
        }
      }
      case Nil => None
    }
  }

  def getAllModels(orderBy: String, ascending: Boolean, offset: Int, limit: Int): List[ModelMetaData] = {
    val db = dbPool.acquire()
    val query = new OSQLSynchQuery[ODocument]("SELECT modelId, collectionId, version, created, modified FROM model")
    val result: java.util.List[ODocument] = db.command(query).execute()
    result.asScala.toList map { doc => ModelMetaData(ModelFqn(doc.field("collectionId"), doc.field("modelId")), doc.field("version", OType.LONG), doc.field("created", OType.LONG), doc.field("modified", OType.LONG)) }
  }

  def getAllModelsInCollection(collectionId: String, orderBy: String, ascending: Boolean, offset: Int, limit: Int): List[ModelMetaData] = {
    val db = dbPool.acquire()
    val query = new OSQLSynchQuery[ODocument]("SELECT modelId, collectionId, version, created, modified FROM model where collectionId = :collectionId")
    val params: java.util.Map[String, String] = HashMap("collectionid" -> collectionId)
    val result: java.util.List[ODocument] = db.command(query).execute(params)
    result.asScala.toList map { doc => ModelMetaData(ModelFqn(collectionId, doc.field("modelId")), doc.field("version", OType.LONG), doc.field("created", OType.LONG), doc.field("modified", OType.LONG)) }
  }
}  