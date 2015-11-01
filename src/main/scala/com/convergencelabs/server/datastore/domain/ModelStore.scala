package com.convergencelabs.server.datastore.domain

import java.time.Instant
import java.util.{Map => JMap}

import scala.collection.JavaConversions._
import scala.collection.JavaConverters._
import scala.collection.immutable.HashMap

import org.json4s._
import org.json4s.JsonAST.JNumber
import org.json4s.JsonAST.JValue
import org.json4s.JsonAST.JValue
import org.json4s.NoTypeHints
import org.json4s.jackson.JsonMethods._
import org.json4s.jackson.Serialization
import org.json4s.jackson.Serialization.read
import org.json4s.jackson.Serialization.write

import com.convergencelabs.server.domain.model.ModelFqn
import com.convergencelabs.server.domain.model.ModelFqn
import com.convergencelabs.server.domain.model.ot.ops.ArrayInsertOperation
import com.convergencelabs.server.domain.model.ot.ops.ArrayMoveOperation
import com.convergencelabs.server.domain.model.ot.ops.ArrayRemoveOperation
import com.convergencelabs.server.domain.model.ot.ops.ArrayReplaceOperation
import com.convergencelabs.server.domain.model.ot.ops.CompoundOperation
import com.convergencelabs.server.domain.model.ot.ops.NumberAddOperation
import com.convergencelabs.server.domain.model.ot.ops.ObjectRemovePropertyOperation
import com.convergencelabs.server.domain.model.ot.ops.ObjectSetOperation
import com.convergencelabs.server.domain.model.ot.ops.ObjectSetPropertyOperation
import com.convergencelabs.server.domain.model.ot.ops.Operation
import com.convergencelabs.server.domain.model.ot.ops.Operation
import com.convergencelabs.server.domain.model.ot.ops.StringInsertOperation
import com.convergencelabs.server.domain.model.ot.ops.StringRemoveOperation
import com.convergencelabs.server.util.JValueMapper
import com.orientechnologies.orient.core.db.OPartitionedDatabasePool
import com.orientechnologies.orient.core.metadata.schema.OType
import com.orientechnologies.orient.core.record.impl.ODocument
import com.orientechnologies.orient.core.sql.OCommandSQL
import com.orientechnologies.orient.core.sql.query.OSQLSynchQuery

object ModelStore {
  val CollectionId = "collectionId"
  val ModelId = "modelId"
  val CreatedTime = "createdTime"
  val ModifiedTime = "modifiedTime"
  val Version = "version"
  val Data = "data"

  def toOrientPath(path: List[Any]): String = {
    val pathBuilder = new StringBuilder();
    pathBuilder.append(Data);
    path.foreach { p =>
      p match {
        case p: Int    => pathBuilder.append("[").append(p).append("]")
        case p: String => pathBuilder.append(".").append(p)
      }
    }
    return pathBuilder.toString();
  }
}

class ModelStore(dbPool: OPartitionedDatabasePool) {

  private[this] implicit val formats = Serialization.formats(NoTypeHints)

  def modelExists(fqn: ModelFqn): Boolean = {
    val db = dbPool.acquire()
    val query = new OSQLSynchQuery[ODocument]("SELECT modelId FROM model WHERE collectionId = :collectionId and modelId = :modelId")
    val params: java.util.Map[String, String] = HashMap("collectionId" -> fqn.collectionId, "modelId" -> fqn.modelId)
    val result: java.util.List[ODocument] = db.command(query).execute(params)
    db.close()
    !result.isEmpty()
  }

  def createModel(fqn: ModelFqn, data: JValue, creationTime: Instant): Unit = {
    val db = dbPool.acquire()
    val dataObject = JObject(List((ModelStore.Data, data)))
    val doc = db.newInstance("model")
    doc.fromJSON(compact(render(dataObject)))
    doc.field(ModelStore.ModelId, fqn.modelId)
    doc.field(ModelStore.CollectionId, fqn.collectionId)
    doc.field(ModelStore.Version, 0)
    doc.field(ModelStore.CreatedTime, creationTime.toEpochMilli()) // FIXME Update database to use datetime
    doc.field(ModelStore.ModifiedTime, creationTime.toEpochMilli()) // FIXME Update database to use datetime
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
      case doc :: rest => Some(docToModelMetaData(doc))
      case Nil         => None
    }
  }

  def getModelData(fqn: ModelFqn): Option[ModelData] = {
    val db = dbPool.acquire()
    val query = new OSQLSynchQuery[ODocument]("SELECT FROM model WHERE collectionId = :collectionId and modelId = :modelId")
    val params: java.util.Map[String, String] = HashMap("collectionId" -> fqn.collectionId, "modelId" -> fqn.modelId)
    val result: java.util.List[ODocument] = db.command(query).execute(params)
    result.asScala.toList match {
      case doc :: Nil => Some(docToModelData(doc))
      case Nil        => None
      case _          => None // FIXME Log
    }
  }

  def getModelJsonData(fqn: ModelFqn): Option[JValue] = {
    val db = dbPool.acquire()
    val query = new OSQLSynchQuery[ODocument]("SELECT data FROM model WHERE collectionId = :collectionId and modelId = :modelId")
    val params: java.util.Map[String, String] = HashMap("collectionId" -> fqn.collectionId, "modelId" -> fqn.modelId)
    val result: java.util.List[ODocument] = db.command(query).execute(params)
    result.asScala.toList match {
      case doc :: rest => Some(parse(doc.toJSON()) \\ ModelStore.Data)
      case Nil         => None
    }
  }

  def applyOperationToModel(fqn: ModelFqn, operation: Operation, version: Long, timestamp: Long, username: String): Unit = {
    operation match {
      case compoundOp: CompoundOperation     => compoundOp.operations foreach { op => applyOperationToModel(fqn, op, version, timestamp, username) }
      case op: ArrayInsertOperation          => applyArrayInsertOperation(fqn, op)
      case op: ArrayRemoveOperation          => applyArrayRemoveOperation(fqn, op)
      case op: ArrayReplaceOperation         => applyArrayReplaceOperation(fqn, op)
      case op: ArrayMoveOperation            => applyArrayMoveOperation(fqn, op)
      case op: ObjectSetPropertyOperation    => applyObjectSetPropertyOperation(fqn, op)
      case op: ObjectRemovePropertyOperation => applyObjectRemovePropertyOperation(fqn, op)
      case op: ObjectSetOperation            => applyObjectSetOperation(fqn, op)
      case op: StringInsertOperation         => applyStringInsertOperation(fqn, op)
      case op: StringRemoveOperation         => applyStringRemoveOperation(fqn, op)
      case op: NumberAddOperation            => applyNumberAddOperation(fqn, op)
    }
  }

  def applyArrayInsertOperation(fqn: ModelFqn, operation: ArrayInsertOperation): Unit = {
    val db = dbPool.acquire()
    val pathString = ModelStore.toOrientPath(operation.path)

    val params: java.util.Map[String, Any] = HashMap("collectionId" -> fqn.collectionId, "modelId" -> fqn.modelId, "index" -> operation.index, "value" -> JValueMapper.jValueToJava(operation.value))
    val value = write(operation.value)
    val updateCommand = new OCommandSQL(s"UPDATE model SET $pathString = arrayInsert($pathString, :index, $value) WHERE collectionId = :collectionId and modelId = :modelId")
    db.command(updateCommand).execute(params)
  }

  def applyArrayRemoveOperation(fqn: ModelFqn, operation: ArrayRemoveOperation): Unit = {
    val db = dbPool.acquire()
    val pathString = ModelStore.toOrientPath(operation.path)
    val query = new OSQLSynchQuery[ODocument](s"SELECT $pathString FROM model WHERE collectionId = :collectionId and modelId = :modelId")

    val params: java.util.Map[String, Any] = HashMap("collectionId" -> fqn.collectionId, "modelId" -> fqn.modelId)

    val result: java.util.List[ODocument] = db.command(query).execute(params)

    result.asScala.toList match {
      case doc :: Nil => {
        val arrayDocument: List[Any] = doc.field("data")
        arrayDocument.remove(operation.index);

        val updateCommand = new OCommandSQL(s"UPDATE model SET $pathString = :value WHERE collectionId = :collectionId and modelId = :modelId")
        params += ("value" -> arrayDocument)
        db.command(updateCommand).execute(params);
      }
      case _ => ???
    }
  }

  def applyArrayReplaceOperation(fqn: ModelFqn, operation: ArrayReplaceOperation): Unit = {
    val db = dbPool.acquire()
    val pathString = ModelStore.toOrientPath(operation.path)
    val query = new OSQLSynchQuery[ODocument](s"SELECT $pathString FROM model WHERE collectionId = :collectionId and modelId = :modelId")

    val params: java.util.Map[String, Any] = HashMap("collectionId" -> fqn.collectionId, "modelId" -> fqn.modelId)

    val result: java.util.List[ODocument] = db.command(query).execute(params)

    result.asScala.toList match {
      case doc :: Nil => {
        val arrayDocument: List[Any] = doc.field("data")
        val newElement = JValueMapper.jValueToJava(operation.newValue);
        arrayDocument.set(operation.index, newElement);

        val updateCommand = new OCommandSQL(s"UPDATE model SET $pathString = :value WHERE collectionId = :collectionId and modelId = :modelId")
        params += ("value" -> arrayDocument)
        db.command(updateCommand).execute(params);
      }
      case _ => ???
    }
  }

  def applyArrayMoveOperation(fqn: ModelFqn, operation: ArrayMoveOperation): Unit = {
    val db = dbPool.acquire()
    val pathString = ModelStore.toOrientPath(operation.path)
    val query = new OSQLSynchQuery[ODocument](s"SELECT $pathString FROM model WHERE collectionId = :collectionId and modelId = :modelId")

    val params: java.util.Map[String, Any] = HashMap("collectionId" -> fqn.collectionId, "modelId" -> fqn.modelId)

    val result: java.util.List[ODocument] = db.command(query).execute(params)

    result.asScala.toList match {
      case doc :: Nil => {
        val arrayDocument: List[Any] = doc.field("data")
        arrayDocument.add(operation.toIndex, arrayDocument.remove(operation.fromIndex));

        val updateCommand = new OCommandSQL(s"UPDATE model SET $pathString = :value WHERE collectionId = :collectionId and modelId = :modelId")
        params += ("value" -> arrayDocument)
        db.command(updateCommand).execute(params);
      }
      case _ => ???
    }
  }

  def applyObjectSetPropertyOperation(fqn: ModelFqn, operation: ObjectSetPropertyOperation): Unit = {
    val db = dbPool.acquire()
    val pathString = ModelStore.toOrientPath(operation.path)
    val params: java.util.Map[String, Any] = HashMap("collectionId" -> fqn.collectionId, "modelId" -> fqn.modelId)

    val updateCommand = new OCommandSQL(s"UPDATE model SET $pathString = :value WHERE collectionId = :collectionId and modelId = :modelId")
    params += ("value" -> JValueMapper.jValueToJava(operation.newValue))
    db.command(updateCommand).execute(params)
  }

  def applyObjectRemovePropertyOperation(fqn: ModelFqn, operation: ObjectRemovePropertyOperation): Unit = {
    val db = dbPool.acquire()
    val pathString = ModelStore.toOrientPath(operation.path)
    val params: java.util.Map[String, Any] = HashMap("collectionId" -> fqn.collectionId, "modelId" -> fqn.modelId)

    val updateCommand = new OCommandSQL(s"UPDATE model REMOVE $pathString = :property WHERE collectionId = :collectionId and modelId = :modelId")
    params += ("property" -> operation.property)
    db.command(updateCommand).execute(params)
  }

  def applyObjectSetOperation(fqn: ModelFqn, operation: ObjectSetOperation): Unit = {
    val db = dbPool.acquire()
    val pathString = ModelStore.toOrientPath(operation.path)
    val params: java.util.Map[String, Any] = HashMap("collectionId" -> fqn.collectionId, "modelId" -> fqn.modelId)

    val updateCommand = new OCommandSQL(s"UPDATE model SET $pathString = :value WHERE collectionId = :collectionId and modelId = :modelId")
    params += ("value" -> JValueMapper.jValueToJava(operation.newValue))
    db.command(updateCommand).execute(params)
  }

  def applyStringInsertOperation(fqn: ModelFqn, operation: StringInsertOperation): Unit = {
    val db = dbPool.acquire()
    val pathString = ModelStore.toOrientPath(operation.path)
    val params: java.util.Map[String, Any] = HashMap("collectionId" -> fqn.collectionId, "modelId" -> fqn.modelId, "index" -> operation.index, "value" -> operation.value)

    val updateCommand = new OCommandSQL(s"UPDATE model SET $pathString = stringInsert($pathString, :index, :value) WHERE collectionId = :collectionId and modelId = :modelId")
    db.command(updateCommand).execute(params)
  }

  def applyStringRemoveOperation(fqn: ModelFqn, operation: StringRemoveOperation): Unit = {
    val db = dbPool.acquire()
    val pathString = ModelStore.toOrientPath(operation.path)
    val params: java.util.Map[String, Any] = HashMap("collectionId" -> fqn.collectionId, "modelId" -> fqn.modelId, "index" -> operation.index, "length" -> operation.value.length())

    val updateCommand = new OCommandSQL(s"UPDATE model SET $pathString = stringRemove($pathString, :index, :length) WHERE collectionId = :collectionId and modelId = :modelId")
    db.command(updateCommand).execute(params)
  }

  def applyNumberAddOperation(fqn: ModelFqn, operation: NumberAddOperation): Unit = {
    val db = dbPool.acquire()
    val pathString = ModelStore.toOrientPath(operation.path)
    val params: java.util.Map[String, Any] = HashMap("collectionId" -> fqn.collectionId, "modelId" -> fqn.modelId, "value" -> operation.value)
    val updateCommand = new OCommandSQL(s"UPDATE model INCREMENT $pathString = :value WHERE collectionId = :collectionId and modelId = :modelId");
    db.command(updateCommand).execute(params);
  }

  def getModelFieldDataType(fqn: ModelFqn, path: List[Any]): Option[DataType.Value] = {
    val db = dbPool.acquire()
    val pathString = ModelStore.toOrientPath(path)
    val query = new OSQLSynchQuery[ODocument](s"SELECT pathString FROM model WHERE collectionId = :collectionId and modelId = :modelId")
    val params: java.util.Map[String, String] = HashMap("collectionId" -> fqn.collectionId, "modelId" -> fqn.modelId)
    val result: java.util.List[ODocument] = db.command(query).execute(params)
    result.asScala.toList match {
      case doc :: rest => {
        (parse(doc.toJSON()) \\ ModelStore.Data) match {
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
    result.asScala.toList map { doc => docToModelMetaData(doc) }
  }

  def getAllModelsInCollection(collectionId: String, orderBy: String, ascending: Boolean, offset: Int, limit: Int): List[ModelMetaData] = {
    val db = dbPool.acquire()
    val query = new OSQLSynchQuery[ODocument]("SELECT modelId, collectionId, version, created, modified FROM model where collectionId = :collectionId")
    val params: java.util.Map[String, String] = HashMap("collectionid" -> collectionId)
    val result: java.util.List[ODocument] = db.command(query).execute(params)
    result.asScala.toList map { doc => docToModelMetaData(doc) }
  }

  def docToModelData(doc: ODocument): ModelData = {
    val modelData: JMap[String, Any] = doc.field("data", OType.EMBEDDEDMAP)
    ModelData(
      ModelMetaData(
        ModelFqn(doc.field("modelId"), doc.field("collectionId")),
        doc.field("version", OType.LONG),
        Instant.ofEpochMilli(doc.field("creationTime", OType.LONG)), // FIXME make a date in the DB
        Instant.ofEpochMilli(doc.field("modifiedTime", OType.LONG))), // FIXME make a date in the DB
      JValueMapper.javaToJValue(modelData)) 
  }

  def docToModelMetaData(doc: ODocument): ModelMetaData = {
    ModelMetaData(
      ModelFqn(doc.field("modelId"), doc.field("collectionId")),
      doc.field("version", OType.LONG),
      Instant.ofEpochMilli(doc.field("creationTime", OType.LONG)), // FIXME make a date in the DB
      Instant.ofEpochMilli(doc.field("modifiedTime", OType.LONG))) // FIXME make a date in the DB
  }
}

// FIXME review these names
case class ModelData(metaData: ModelMetaData, data: JValue)
case class ModelMetaData(fqn: ModelFqn, version: Long, createdTime: Instant, modifiedTime: Instant)

object DataType extends Enumeration {
  val ARRAY, OBJECT, STRING, NUMBER, BOOLEAN, NULL = Value
}
