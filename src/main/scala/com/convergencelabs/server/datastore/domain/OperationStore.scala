package com.convergencelabs.server.datastore.domain

import java.time.Instant
import java.util.{ Map => JMap }
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
import com.orientechnologies.orient.core.db.record.OTrackedList
import com.convergencelabs.server.domain.model.ot.ops.ArraySetOperation
import com.convergencelabs.server.domain.model.ot.ops.StringSetOperation
import com.convergencelabs.server.domain.model.ot.ops.NumberSetOperation
import com.convergencelabs.server.domain.model.ot.ops.ArraySetOperation
import com.convergencelabs.server.domain.model.ot.ops.ObjectAddPropertyOperation
import com.convergencelabs.server.datastore.domain.OperationStore.Fields._
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx

object OperationStore {
  val ModelOperation = "ModelOperation"

  object Fields {
    val Version = "version"
    val ModelId = "modelId"
    val CollectionId = "collectionId"
    val Timestamp = "collectionId"
    val Uid = "uid"
    val Sid = "sid"
    val Operation = "op"
    val Data = "data"
  }

  def toOrientPath(path: List[Any]): String = {
    val pathBuilder = new StringBuilder()
    pathBuilder.append(Data)
    path.foreach { p =>
      p match {
        case p: Int    => pathBuilder.append(s"[$p]")
        case p: String => pathBuilder.append(s".$p")
      }
    }
    return pathBuilder.toString()
  }
}

class OperationStore(dbPool: OPartitionedDatabasePool) {

  private[this] implicit val formats = Serialization.formats(NoTypeHints)

  def processOperation(fqn: ModelFqn, operation: Operation, version: Long, timestamp: Instant, uid: String, sid: String): Unit = {
    val db = dbPool.acquire()
    db.begin()
    val doc = db.newInstance(OperationStore.ModelOperation)
    doc.field(CollectionId, fqn.collectionId)
    doc.field(ModelId, fqn.modelId)
    doc.field(Version, version)
    doc.field(Timestamp, timestamp)
    doc.field(Uid, uid)
    doc.field(Sid, sid)
    doc.field(Operation, OrientDBOperationMapper.operationToMap(operation))
    doc.save()
    applyOperationToModel(fqn, operation, db)
    db.commit()
  }

  private[this] def applyOperationToModel(fqn: ModelFqn, operation: Operation, db: ODatabaseDocumentTx): Unit = {
    operation match {
      case compoundOp: CompoundOperation     => compoundOp.operations foreach { op => applyOperationToModel(fqn, op, db) }
      case op: ArrayInsertOperation          => applyArrayInsertOperation(fqn, op, db)
      case op: ArrayRemoveOperation          => applyArrayRemoveOperation(fqn, op, db)
      case op: ArrayReplaceOperation         => applyArrayReplaceOperation(fqn, op, db)
      case op: ArrayMoveOperation            => applyArrayMoveOperation(fqn, op, db)
      case op: ArraySetOperation             => applyArraySetOperation(fqn, op, db)
      case op: ObjectAddPropertyOperation    => applyObjectAddPropertyOperation(fqn, op, db)
      case op: ObjectSetPropertyOperation    => applyObjectSetPropertyOperation(fqn, op, db)
      case op: ObjectRemovePropertyOperation => applyObjectRemovePropertyOperation(fqn, op, db)
      case op: ObjectSetOperation            => applyObjectSetOperation(fqn, op, db)
      case op: StringInsertOperation         => applyStringInsertOperation(fqn, op, db)
      case op: StringRemoveOperation         => applyStringRemoveOperation(fqn, op, db)
      case op: StringSetOperation            => applyStringSetOperation(fqn, op, db)
      case op: NumberAddOperation            => applyNumberAddOperation(fqn, op, db)
      case op: NumberSetOperation            => applyNumberSetOperation(fqn, op, db)
    }
  }

  private[this] def applyArrayInsertOperation(fqn: ModelFqn, operation: ArrayInsertOperation, db: ODatabaseDocumentTx): Unit = {
    val pathString = ModelStore.toOrientPath(operation.path)
    val params: java.util.Map[String, Any] = HashMap("collectionId" -> fqn.collectionId, "modelId" -> fqn.modelId, "index" -> operation.index, "value" -> JValueMapper.jValueToJava(operation.value))
    val value = write(operation.value)
    val updateCommand = new OCommandSQL(s"UPDATE model SET $pathString = arrayInsert($pathString, :index, $value) WHERE collectionId = :collectionId and modelId = :modelId")
    db.command(updateCommand).execute(params)
  }

  private[this] def applyArrayRemoveOperation(fqn: ModelFqn, operation: ArrayRemoveOperation, db: ODatabaseDocumentTx): Unit = {
    val pathString = ModelStore.toOrientPath(operation.path)
    val params: java.util.Map[String, Any] = HashMap("collectionId" -> fqn.collectionId, "modelId" -> fqn.modelId, "index" -> operation.index)
    val updateCommand = new OCommandSQL(s"UPDATE model SET $pathString = arrayRemove($pathString, :index) WHERE collectionId = :collectionId and modelId = :modelId")
    db.command(updateCommand).execute(params)
  }

  private[this] def applyArrayReplaceOperation(fqn: ModelFqn, operation: ArrayReplaceOperation, db: ODatabaseDocumentTx): Unit = {
    val pathString = ModelStore.toOrientPath(operation.path)
    val params: java.util.Map[String, Any] = HashMap("collectionId" -> fqn.collectionId, "modelId" -> fqn.modelId, "index" -> operation.index, "value" -> JValueMapper.jValueToJava(operation.newValue))
    val value = write(operation.newValue)
    val updateCommand = new OCommandSQL(s"UPDATE model SET $pathString = arrayReplace($pathString, :index, $value) WHERE collectionId = :collectionId and modelId = :modelId")
    db.command(updateCommand).execute(params)
  }

  private[this] def applyArrayMoveOperation(fqn: ModelFqn, operation: ArrayMoveOperation, db: ODatabaseDocumentTx): Unit = {
    val pathString = ModelStore.toOrientPath(operation.path)
    val params: java.util.Map[String, Any] = HashMap("collectionId" -> fqn.collectionId, "modelId" -> fqn.modelId, "fromIndex" -> operation.fromIndex, "toIndex" -> operation.toIndex)
    val updateCommand = new OCommandSQL(s"UPDATE model SET $pathString = arrayMove($pathString, :fromIndex, :toIndex) WHERE collectionId = :collectionId and modelId = :modelId")
    db.command(updateCommand).execute(params)
  }

  //TODO: Validate that data being replaced is an array.
  private[this] def applyArraySetOperation(fqn: ModelFqn, operation: ArraySetOperation, db: ODatabaseDocumentTx): Unit = {
    val pathString = ModelStore.toOrientPath(operation.path)
    val params: java.util.Map[String, Any] = HashMap("collectionId" -> fqn.collectionId, "modelId" -> fqn.modelId, "value" -> JValueMapper.jValueToJava(operation.newValue))
    val updateCommand = new OCommandSQL(s"UPDATE model SET $pathString = :value WHERE collectionId = :collectionId and modelId = :modelId")
    db.command(updateCommand).execute(params)
  }

  private[this] def applyObjectAddPropertyOperation(fqn: ModelFqn, operation: ObjectAddPropertyOperation, db: ODatabaseDocumentTx): Unit = {
    val pathString = ModelStore.toOrientPath(operation.path)
    val params: java.util.Map[String, Any] = HashMap("collectionId" -> fqn.collectionId, "modelId" -> fqn.modelId, "value" -> JValueMapper.jValueToJava(operation.newValue))
    val updateCommand = new OCommandSQL(s"UPDATE model SET $pathString = :value WHERE collectionId = :collectionId and modelId = :modelId")
    db.command(updateCommand).execute(params)
  }

  private[this] def applyObjectSetPropertyOperation(fqn: ModelFqn, operation: ObjectSetPropertyOperation, db: ODatabaseDocumentTx): Unit = {
    val pathString = ModelStore.toOrientPath(operation.path)
    val params: java.util.Map[String, Any] = HashMap("collectionId" -> fqn.collectionId, "modelId" -> fqn.modelId, "value" -> JValueMapper.jValueToJava(operation.newValue))
    val updateCommand = new OCommandSQL(s"UPDATE model SET $pathString = :value WHERE collectionId = :collectionId and modelId = :modelId")
    db.command(updateCommand).execute(params)
  }

  private[this] def applyObjectRemovePropertyOperation(fqn: ModelFqn, operation: ObjectRemovePropertyOperation, db: ODatabaseDocumentTx): Unit = {
    val pathString = ModelStore.toOrientPath(operation.path)
    val params: java.util.Map[String, Any] = HashMap("collectionId" -> fqn.collectionId, "modelId" -> fqn.modelId, "property" -> operation.property)
    val updateCommand = new OCommandSQL(s"UPDATE model REMOVE $pathString = :property WHERE collectionId = :collectionId and modelId = :modelId")
    db.command(updateCommand).execute(params)
  }

  private[this] def applyObjectSetOperation(fqn: ModelFqn, operation: ObjectSetOperation, db: ODatabaseDocumentTx): Unit = {
    val pathString = ModelStore.toOrientPath(operation.path)
    val params: java.util.Map[String, Any] = HashMap("collectionId" -> fqn.collectionId, "modelId" -> fqn.modelId, "value" -> JValueMapper.jValueToJava(operation.newValue))
    val updateCommand = new OCommandSQL(s"UPDATE model SET $pathString = :value WHERE collectionId = :collectionId and modelId = :modelId")
    db.command(updateCommand).execute(params)
  }

  private[this] def applyStringInsertOperation(fqn: ModelFqn, operation: StringInsertOperation, db: ODatabaseDocumentTx): Unit = {
    val pathString = ModelStore.toOrientPath(operation.path)
    val params: java.util.Map[String, Any] = HashMap("collectionId" -> fqn.collectionId, "modelId" -> fqn.modelId, "index" -> operation.index, "value" -> operation.value)
    val updateCommand = new OCommandSQL(s"UPDATE model SET $pathString = stringInsert($pathString, :index, :value) WHERE collectionId = :collectionId and modelId = :modelId")
    db.command(updateCommand).execute(params)
  }

  private[this] def applyStringRemoveOperation(fqn: ModelFqn, operation: StringRemoveOperation, db: ODatabaseDocumentTx): Unit = {
    val pathString = ModelStore.toOrientPath(operation.path)
    val params: java.util.Map[String, Any] = HashMap("collectionId" -> fqn.collectionId, "modelId" -> fqn.modelId, "index" -> operation.index, "length" -> operation.value.length())
    val updateCommand = new OCommandSQL(s"UPDATE model SET $pathString = stringRemove($pathString, :index, :length) WHERE collectionId = :collectionId and modelId = :modelId")
    db.command(updateCommand).execute(params)
  }

  private[this] def applyStringSetOperation(fqn: ModelFqn, operation: StringSetOperation, db: ODatabaseDocumentTx): Unit = {
    val pathString = ModelStore.toOrientPath(operation.path)
    val params: java.util.Map[String, Any] = HashMap("collectionId" -> fqn.collectionId, "modelId" -> fqn.modelId, "value" -> operation.value)
    val updateCommand = new OCommandSQL(s"UPDATE model SET $pathString = :value WHERE collectionId = :collectionId and modelId = :modelId")
    db.command(updateCommand).execute(params)
  }

  private[this] def applyNumberAddOperation(fqn: ModelFqn, operation: NumberAddOperation, db: ODatabaseDocumentTx): Unit = {
    val pathString = ModelStore.toOrientPath(operation.path)
    val params: java.util.Map[String, Any] = HashMap("collectionId" -> fqn.collectionId, "modelId" -> fqn.modelId, "value" -> operation.value)
    val updateCommand = new OCommandSQL(s"UPDATE model INCREMENT $pathString = :value WHERE collectionId = :collectionId and modelId = :modelId")
    db.command(updateCommand).execute(params)
  }

  //TODO: Determine strategy for handling numbers correctly
  private[this] def applyNumberSetOperation(fqn: ModelFqn, operation: NumberSetOperation, db: ODatabaseDocumentTx): Unit = {
    val pathString = ModelStore.toOrientPath(operation.path)
    val params: java.util.Map[String, Any] = HashMap("collectionId" -> fqn.collectionId, "modelId" -> fqn.modelId, "value" -> operation.newValue)
    val updateCommand = new OCommandSQL(s"UPDATE model SET $pathString = :value WHERE collectionId = :collectionId and modelId = :modelId")
    db.command(updateCommand).execute(params)
  }
}
