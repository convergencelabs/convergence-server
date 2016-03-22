package com.convergencelabs.server.datastore.domain

import java.time.Instant
import java.util.Date

import scala.collection.JavaConverters.mapAsJavaMapConverter
import scala.util.Try

import org.json4s.NoTypeHints
import org.json4s.jackson.Serialization
import org.json4s.jackson.Serialization.write

import com.convergencelabs.server.datastore.AbstractDatabasePersistence
import com.convergencelabs.server.datastore.domain.OrientPathUtil.appendToPath
import com.convergencelabs.server.datastore.domain.OrientPathUtil.toOrientPath
import com.convergencelabs.server.datastore.domain.OrientPathUtil.escape
import com.convergencelabs.server.domain.model.ModelFqn
import com.convergencelabs.server.domain.model.ModelOperation
import com.convergencelabs.server.domain.model.ot.ArrayInsertOperation
import com.convergencelabs.server.domain.model.ot.ArrayMoveOperation
import com.convergencelabs.server.domain.model.ot.ArrayRemoveOperation
import com.convergencelabs.server.domain.model.ot.ArrayReplaceOperation
import com.convergencelabs.server.domain.model.ot.ArraySetOperation
import com.convergencelabs.server.domain.model.ot.BooleanSetOperation
import com.convergencelabs.server.domain.model.ot.CompoundOperation
import com.convergencelabs.server.domain.model.ot.DiscreteOperation
import com.convergencelabs.server.domain.model.ot.NumberAddOperation
import com.convergencelabs.server.domain.model.ot.NumberSetOperation
import com.convergencelabs.server.domain.model.ot.ObjectAddPropertyOperation
import com.convergencelabs.server.domain.model.ot.ObjectRemovePropertyOperation
import com.convergencelabs.server.domain.model.ot.ObjectSetOperation
import com.convergencelabs.server.domain.model.ot.ObjectSetPropertyOperation
import com.convergencelabs.server.domain.model.ot.Operation
import com.convergencelabs.server.domain.model.ot.StringInsertOperation
import com.convergencelabs.server.domain.model.ot.StringRemoveOperation
import com.convergencelabs.server.domain.model.ot.StringSetOperation
import com.convergencelabs.server.util.JValueMapper
import com.orientechnologies.orient.core.db.OPartitionedDatabasePool
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx
import com.orientechnologies.orient.core.sql.OCommandSQL

class ModelOperationProcessor private[domain] (dbPool: OPartitionedDatabasePool)
    extends AbstractDatabasePersistence(dbPool) {

  val CollectionId = "collectionId"
  val ModelId = "modelId"
  val Value = "value"
  val Index = "index"

  private[this] implicit val formats = Serialization.formats(NoTypeHints)

  def processModelOperation(modelOperation: ModelOperation): Try[Unit] = tryWithDb { db =>
    db.begin()

    // Apply the op.
    applyOperationToModel(modelOperation.modelFqn, modelOperation.op, db)

    // Persist the operation
    // FIXME this causes an exception
    //db.save(modelOperation.asODocument)

    // Update the model metadata
    updateModelMetaData(modelOperation.modelFqn, modelOperation.timestamp, db)

    db.commit()
    
    Unit
  }

  private[this] def updateModelMetaData(fqn: ModelFqn, timestamp: Instant, db: ODatabaseDocumentTx): Unit = {
    val queryString = 
      """UPDATE Model SET
        |  version = eval('version + 1'),
        |  modifiedTime = :timestamp
        |WHERE
        |  collectionId = :collectionId AND
        |  modelId = :modelId""".stripMargin
    
    val updateCommand = new OCommandSQL(queryString)
    
    val params = Map(
        CollectionId -> fqn.collectionId, 
        ModelId -> fqn.modelId, 
        "timestamp" -> Date.from(timestamp))
        
    val result: Object = db.command(updateCommand).execute(params.asJava)
    
    Unit
  }

  // scalastyle:off cyclomatic.complexity
  private[this] def applyOperationToModel(fqn: ModelFqn, operation: Operation, db: ODatabaseDocumentTx): Unit = {
    operation match {
      case op: CompoundOperation => op.operations foreach { o => applyOperationToModel(fqn, o, db) }
      case op: DiscreteOperation if op.noOp => // Do nothing since this is a noOp

      case op: ArrayInsertOperation => applyArrayInsertOperation(fqn, op, db)
      case op: ArrayRemoveOperation => applyArrayRemoveOperation(fqn, op, db)
      case op: ArrayReplaceOperation => applyArrayReplaceOperation(fqn, op, db)
      case op: ArrayMoveOperation => applyArrayMoveOperation(fqn, op, db)
      case op: ArraySetOperation => applyArraySetOperation(fqn, op, db)

      case op: ObjectAddPropertyOperation => applyObjectAddPropertyOperation(fqn, op, db)
      case op: ObjectSetPropertyOperation => applyObjectSetPropertyOperation(fqn, op, db)
      case op: ObjectRemovePropertyOperation => applyObjectRemovePropertyOperation(fqn, op, db)
      case op: ObjectSetOperation => applyObjectSetOperation(fqn, op, db)

      case op: StringInsertOperation => applyStringInsertOperation(fqn, op, db)
      case op: StringRemoveOperation => applyStringRemoveOperation(fqn, op, db)
      case op: StringSetOperation => applyStringSetOperation(fqn, op, db)

      case op: NumberAddOperation => applyNumberAddOperation(fqn, op, db)
      case op: NumberSetOperation => applyNumberSetOperation(fqn, op, db)

      case op: BooleanSetOperation => applyBooleanSetOperation(fqn, op, db)
    }
  }
  // scalastyle:on cyclomatic.complexity

  private[this] def applyArrayInsertOperation(fqn: ModelFqn, operation: ArrayInsertOperation, db: ODatabaseDocumentTx): Unit = {
//    val pathString = escape(toOrientPath(operation.path))
//    val params = Map(CollectionId -> fqn.collectionId, ModelId -> fqn.modelId, Index -> operation.index, Value -> JValueMapper.jValueToJava(operation.value))
//    val value = write(operation.value)
//    val queryString = s"UPDATE Model SET $pathString = arrayInsert($pathString, :index, $value) WHERE collectionId = :collectionId and modelId = :modelId"
//    val updateCommand = new OCommandSQL(queryString)
//    db.command(updateCommand).execute(params.asJava)
  }

  private[this] def applyArrayRemoveOperation(fqn: ModelFqn, operation: ArrayRemoveOperation, db: ODatabaseDocumentTx): Unit = {
//    val pathString = escape(toOrientPath(operation.path))
//    val params = Map(CollectionId -> fqn.collectionId, ModelId -> fqn.modelId, Index -> operation.index)
//    val queryString = s"UPDATE Model SET $pathString = arrayRemove($pathString, :index) WHERE collectionId = :collectionId and modelId = :modelId"
//    val updateCommand = new OCommandSQL(queryString)
//    db.command(updateCommand).execute(params.asJava)
  }

  private[this] def applyArrayReplaceOperation(fqn: ModelFqn, operation: ArrayReplaceOperation, db: ODatabaseDocumentTx): Unit = {
//    val pathString = escape(toOrientPath(operation.path))
//    val params = Map(CollectionId -> fqn.collectionId, ModelId -> fqn.modelId, Index -> operation.index, Value -> JValueMapper.jValueToJava(operation.value))
//    val value = write(operation.value)
//    val queryString = s"UPDATE Model SET $pathString = arrayReplace($pathString, :index, $value) WHERE collectionId = :collectionId and modelId = :modelId"
//    val updateCommand = new OCommandSQL(queryString)
//    db.command(updateCommand).execute(params.asJava)
  }

  private[this] def applyArrayMoveOperation(fqn: ModelFqn, operation: ArrayMoveOperation, db: ODatabaseDocumentTx): Unit = {
//    val pathString = escape(toOrientPath(operation.path))
//    val params = Map(CollectionId -> fqn.collectionId, ModelId -> fqn.modelId, "fromIndex" -> operation.fromIndex, "toIndex" -> operation.toIndex)
//    val queryString = s"UPDATE Model SET $pathString = arrayMove($pathString, :fromIndex, :toIndex) WHERE collectionId = :collectionId and modelId = :modelId"
//    val updateCommand = new OCommandSQL(queryString)
//    db.command(updateCommand).execute(params.asJava)
  }

  // TODO: Validate that data being replaced is an array.
  private[this] def applyArraySetOperation(fqn: ModelFqn, operation: ArraySetOperation, db: ODatabaseDocumentTx): Unit = {
//    val pathString = escape(toOrientPath(operation.path))
//    val params = Map(CollectionId -> fqn.collectionId, ModelId -> fqn.modelId, Value -> JValueMapper.jValueToJava(operation.value))
//    val queryString = s"UPDATE Model SET $pathString = :value WHERE collectionId = :collectionId and modelId = :modelId"
//    val updateCommand = new OCommandSQL(queryString)
//    db.command(updateCommand).execute(params.asJava)
  }

  private[this] def applyObjectAddPropertyOperation(fqn: ModelFqn, operation: ObjectAddPropertyOperation, db: ODatabaseDocumentTx): Unit = {
//    val pathString = escape(appendToPath(toOrientPath(operation.path), operation.property))
//    val params = Map(CollectionId -> fqn.collectionId, ModelId -> fqn.modelId, Value -> JValueMapper.jValueToJava(operation.value))
//    val updateCommand = new OCommandSQL(s"UPDATE Model SET $pathString = :value WHERE collectionId = :collectionId and modelId = :modelId")
//    db.command(updateCommand).execute(params.asJava)
  }

  private[this] def applyObjectSetPropertyOperation(fqn: ModelFqn, operation: ObjectSetPropertyOperation, db: ODatabaseDocumentTx): Unit = {
//    val pathString = escape(appendToPath(toOrientPath(operation.path), operation.property))
//    val params = Map(CollectionId -> fqn.collectionId, ModelId -> fqn.modelId, Value -> JValueMapper.jValueToJava(operation.value))
//    val updateCommand = new OCommandSQL(s"UPDATE Model SET $pathString = :value WHERE collectionId = :collectionId and modelId = :modelId")
//    db.command(updateCommand).execute(params.asJava)
  }

  private[this] def applyObjectRemovePropertyOperation(fqn: ModelFqn, operation: ObjectRemovePropertyOperation, db: ODatabaseDocumentTx): Unit = {
//    val pathString = escape(toOrientPath(operation.path))
//    val params = Map(CollectionId -> fqn.collectionId, ModelId -> fqn.modelId, "property" -> operation.property)
//    val queryString =
//      s"""UPDATE Model REMOVE
//         |  $pathString = :property
//         |WHERE
//         |  collectionId = :collectionId AND
//         |  modelId = :modelId""".stripMargin
//    val updateCommand = new OCommandSQL(queryString)
//    db.command(updateCommand).execute(params.asJava)
  }

  private[this] def applyObjectSetOperation(fqn: ModelFqn, operation: ObjectSetOperation, db: ODatabaseDocumentTx): Unit = {
//    val pathString = escape(toOrientPath(operation.path))
//    val params = Map(CollectionId -> fqn.collectionId, ModelId -> fqn.modelId, Value -> JValueMapper.jValueToJava(operation.value))
//    val updateCommand = new OCommandSQL(s"UPDATE Model SET $pathString = :value WHERE collectionId = :collectionId and modelId = :modelId")
//    db.command(updateCommand).execute(params.asJava)
  }

  private[this] def applyStringInsertOperation(fqn: ModelFqn, operation: StringInsertOperation, db: ODatabaseDocumentTx): Unit = {
//    val pathString = escape(toOrientPath(operation.path))
//    val params = Map(CollectionId -> fqn.collectionId, ModelId -> fqn.modelId, Index -> operation.index, Value -> operation.value)
//    val queryString = s"UPDATE Model SET $pathString = stringInsert($pathString, :index, :value) WHERE collectionId = :collectionId and modelId = :modelId"
//    val updateCommand = new OCommandSQL(queryString)
//    db.command(updateCommand).execute(params.asJava)
  }

  private[this] def applyStringRemoveOperation(fqn: ModelFqn, operation: StringRemoveOperation, db: ODatabaseDocumentTx): Unit = {
//    val pathString = escape(toOrientPath(operation.path))
//    val params = Map(CollectionId -> fqn.collectionId, ModelId -> fqn.modelId, Index -> operation.index, "length" -> operation.value.length())
//    val queryString = s"UPDATE Model SET $pathString = stringRemove($pathString, :index, :length) WHERE collectionId = :collectionId and modelId = :modelId"
//    val updateCommand = new OCommandSQL(queryString)
//    db.command(updateCommand).execute(params.asJava)
  }

  private[this] def applyStringSetOperation(fqn: ModelFqn, operation: StringSetOperation, db: ODatabaseDocumentTx): Unit = {
//    val pathString = escape(toOrientPath(operation.path))
//    val params = Map(CollectionId -> fqn.collectionId, ModelId -> fqn.modelId, Value -> operation.value)
//    val updateCommand = new OCommandSQL(s"UPDATE Model SET $pathString = :value WHERE collectionId = :collectionId and modelId = :modelId")
//    db.command(updateCommand).execute(params.asJava)
  }

  private[this] def applyNumberAddOperation(fqn: ModelFqn, operation: NumberAddOperation, db: ODatabaseDocumentTx): Unit = {
//    val pathString = escape(toOrientPath(operation.path))
//    val params = Map(CollectionId -> fqn.collectionId, ModelId -> fqn.modelId, Value -> JValueMapper.jNumberToJava(operation.value))
//    val value = JValueMapper.jNumberToJava(operation.value)
//    val updateCommand = new OCommandSQL(
//        s"UPDATE model SET $pathString = eval('$pathString + $value') WHERE collectionId = :collectionId and modelId = :modelId")
//    db.command(updateCommand).execute(params.asJava)
  }

  // TODO: Determine strategy for handling numbers correctly
  private[this] def applyNumberSetOperation(fqn: ModelFqn, operation: NumberSetOperation, db: ODatabaseDocumentTx): Unit = {
//    val pathString = escape(toOrientPath(operation.path))
//    val params = Map(CollectionId -> fqn.collectionId, ModelId -> fqn.modelId, Value -> JValueMapper.jNumberToJava(operation.value))
//    val updateCommand = new OCommandSQL(s"UPDATE Model SET $pathString = :value WHERE collectionId = :collectionId and modelId = :modelId")
//    db.command(updateCommand).execute(params.asJava)
  }

  private[this] def applyBooleanSetOperation(fqn: ModelFqn, operation: BooleanSetOperation, db: ODatabaseDocumentTx): Unit = {
//    val pathString = escape(toOrientPath(operation.path))
//    val params = Map(CollectionId -> fqn.collectionId, ModelId -> fqn.modelId, Value -> operation.value)
//    val updateCommand = new OCommandSQL(s"UPDATE Model SET $pathString = :value WHERE collectionId = :collectionId and modelId = :modelId")
//    db.command(updateCommand).execute(params.asJava)
  }
}
