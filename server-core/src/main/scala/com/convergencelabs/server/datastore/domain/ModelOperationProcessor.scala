package com.convergencelabs.server.datastore.domain

import java.time.Instant
import java.util.Date

import scala.collection.JavaConverters.mapAsJavaMapConverter
import scala.collection.JavaConverters.seqAsJavaListConverter
import scala.util.Try

import com.convergencelabs.server.datastore.AbstractDatabasePersistence
import com.convergencelabs.server.datastore.DatabaseProvider
import com.convergencelabs.server.domain.model.ModelFqn
import com.convergencelabs.server.domain.model.NewModelOperation
import com.convergencelabs.server.domain.model.ot.AppliedArrayInsertOperation
import com.convergencelabs.server.domain.model.ot.AppliedArrayMoveOperation
import com.convergencelabs.server.domain.model.ot.AppliedArrayRemoveOperation
import com.convergencelabs.server.domain.model.ot.AppliedArrayReplaceOperation
import com.convergencelabs.server.domain.model.ot.AppliedArraySetOperation
import com.convergencelabs.server.domain.model.ot.AppliedBooleanSetOperation
import com.convergencelabs.server.domain.model.ot.AppliedCompoundOperation
import com.convergencelabs.server.domain.model.ot.AppliedDiscreteOperation
import com.convergencelabs.server.domain.model.ot.AppliedNumberAddOperation
import com.convergencelabs.server.domain.model.ot.AppliedNumberSetOperation
import com.convergencelabs.server.domain.model.ot.AppliedObjectAddPropertyOperation
import com.convergencelabs.server.domain.model.ot.AppliedObjectRemovePropertyOperation
import com.convergencelabs.server.domain.model.ot.AppliedObjectSetOperation
import com.convergencelabs.server.domain.model.ot.AppliedObjectSetPropertyOperation
import com.convergencelabs.server.domain.model.ot.AppliedOperation
import com.convergencelabs.server.domain.model.ot.AppliedStringInsertOperation
import com.convergencelabs.server.domain.model.ot.AppliedStringRemoveOperation
import com.convergencelabs.server.domain.model.ot.AppliedStringSetOperation
import com.orientechnologies.common.io.OIOUtils
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx
import com.orientechnologies.orient.core.id.ORID
import com.orientechnologies.orient.core.sql.OCommandSQL

import grizzled.slf4j.Logging
import com.convergencelabs.server.datastore.EntityNotFoundException
import com.convergencelabs.server.domain.model.ot.AppliedDateSetOperation

class ModelOperationProcessor private[domain] (
  private[this] val dbProvider: DatabaseProvider,
  private[this] val modelOpStore: ModelOperationStore,
  private[this] val modelStore: ModelStore)
    extends AbstractDatabasePersistence(dbProvider)
    with Logging {

  val Id = "id";
  val CollectionId = "collectionId"
  val ModelId = "modelId"
  val Value = "value"
  val Index = "index"

  def processModelOperation(modelOperation: NewModelOperation): Try[Unit] = tryWithDb { db =>
    // TODO this should all be in a transaction, but orientdb has a problem with this.

    // Apply the op.
    applyOperationToModel(modelOperation.modelFqn, modelOperation.op, db).flatMap { _ =>
      // Persist the operation
      modelOpStore.createModelOperation(modelOperation)
    }.flatMap { _ =>
      // Update the model metadata
      modelStore.updateModelOnOperation(modelOperation.modelFqn.modelId, modelOperation.timestamp)
    }.get
  }
  
  // scalastyle:off cyclomatic.complexity
  private[this] def applyOperationToModel(fqn: ModelFqn, operation: AppliedOperation, db: ODatabaseDocumentTx): Try[Unit] = Try {
    operation match {
      case op: AppliedCompoundOperation => op.operations foreach { o => applyOperationToModel(fqn, o, db) }
      case op: AppliedDiscreteOperation if op.noOp => // Do nothing since this is a noOp

      case op: AppliedArrayInsertOperation => applyArrayInsertOperation(fqn, op, db)
      case op: AppliedArrayRemoveOperation => applyArrayRemoveOperation(fqn, op, db)
      case op: AppliedArrayReplaceOperation => applyArrayReplaceOperation(fqn, op, db)
      case op: AppliedArrayMoveOperation => applyArrayMoveOperation(fqn, op, db)
      case op: AppliedArraySetOperation => applyArraySetOperation(fqn, op, db)

      case op: AppliedObjectAddPropertyOperation => applyObjectAddPropertyOperation(fqn, op, db)
      case op: AppliedObjectSetPropertyOperation => applyObjectSetPropertyOperation(fqn, op, db)
      case op: AppliedObjectRemovePropertyOperation => applyObjectRemovePropertyOperation(fqn, op, db)
      case op: AppliedObjectSetOperation => applyObjectSetOperation(fqn, op, db)

      case op: AppliedStringInsertOperation => applyStringInsertOperation(fqn, op, db)
      case op: AppliedStringRemoveOperation => applyStringRemoveOperation(fqn, op, db)
      case op: AppliedStringSetOperation => applyStringSetOperation(fqn, op, db)

      case op: AppliedNumberAddOperation => applyNumberAddOperation(fqn, op, db)
      case op: AppliedNumberSetOperation => applyNumberSetOperation(fqn, op, db)

      case op: AppliedBooleanSetOperation => applyBooleanSetOperation(fqn, op, db)
      
      case op: AppliedDateSetOperation => applyDateSetOperation(fqn, op, db)
    }
  }
  // scalastyle:on cyclomatic.complexity

  private[this] def applyArrayInsertOperation(fqn: ModelFqn, operation: AppliedArrayInsertOperation, db: ODatabaseDocumentTx): Unit = {
    val value = OrientDataValueBuilder.dataValueToODocument(operation.value, getModelRid(fqn, db))
    value.save()

    val queryString =
      s"""UPDATE ArrayValue SET
             |  children = arrayInsert(children, :index, :value)
             |WHERE
             |  id = :id AND
             |  model.collection.id = :collectionId AND
             |  model.id = :modelId""".stripMargin
    val updateCommand = new OCommandSQL(queryString)
    val params = Map(
      Id -> operation.id,
      CollectionId -> fqn.collectionId,
      ModelId -> fqn.modelId,
      Index -> operation.index,
      Value -> value)
    db.command(updateCommand).execute(params.asJava)
    ()
  }

  private[this] def applyArrayRemoveOperation(
    fqn: ModelFqn, operation: AppliedArrayRemoveOperation, db: ODatabaseDocumentTx): Unit = {
    val queryString =
      s"""UPDATE ArrayValue SET
         |  children = arrayRemove(children, :index)
         |WHERE
         |  id = :id AND
         |  model.collection.id = :collectionId AND
         |  model.id = :modelId""".stripMargin
    val updateCommand = new OCommandSQL(queryString)
    val params = Map(
      Id -> operation.id,
      CollectionId -> fqn.collectionId,
      ModelId -> fqn.modelId,
      Index -> operation.index)
    db.command(updateCommand).execute(params.asJava)
    db.commit()
  }

  private[this] def applyArrayReplaceOperation(
    fqn: ModelFqn, operation: AppliedArrayReplaceOperation, db: ODatabaseDocumentTx): Unit = {
    val value = OrientDataValueBuilder.dataValueToODocument(operation.value, getModelRid(fqn, db))
    value.save()
    db.commit()

    val queryString =
      s"""UPDATE ArrayValue SET
             |  children = arrayReplace(children, :index, :value)
             |WHERE
             |  model.collection.id = :collectionId AND
             |  model.id = :modelId""".stripMargin
    val updateCommand = new OCommandSQL(queryString)

    val params = Map(
      Id -> operation.id,
      CollectionId -> fqn.collectionId,
      ModelId -> fqn.modelId,
      Index -> operation.index,
      Value -> value)
    db.command(updateCommand).execute(params.asJava)
    ()
  }

  private[this] def applyArrayMoveOperation(fqn: ModelFqn, operation: AppliedArrayMoveOperation, db: ODatabaseDocumentTx): Unit = {
    val queryString =
      s"""UPDATE ArrayValue SET
         |  children = arrayMove(children, :fromIndex, :toIndex)
         |WHERE
         |  id = :id AND
         |  model.collection.id = :collectionId AND
         |  model.id = :modelId""".stripMargin
    val updateCommand = new OCommandSQL(queryString)
    val params = Map(
      Id -> operation.id,
      CollectionId -> fqn.collectionId,
      ModelId -> fqn.modelId,
      "fromIndex" -> operation.fromIndex,
      "toIndex" -> operation.toIndex)
    db.command(updateCommand).execute(params.asJava)
    db.commit()
    ()
  }

  private[this] def applyArraySetOperation(fqn: ModelFqn, operation: AppliedArraySetOperation, db: ODatabaseDocumentTx): Unit = {
    val children = operation.value map (v => OrientDataValueBuilder.dataValueToODocument(v, getModelRid(fqn, db)))
    children.foreach { child => child.save() }
    db.commit()

    val params = Map(Id -> operation.id, CollectionId -> fqn.collectionId, ModelId -> fqn.modelId, Value -> children.asJava)
    val queryString =
      s"""UPDATE ArrayValue
             |SET
             |  children = :value
             |WHERE
             |  id = : id AND
             |  model.collection.id = :collectionId AND
             |  model.id = :modelId""".stripMargin
    val updateCommand = new OCommandSQL(queryString)
    db.command(updateCommand).execute(params.asJava)
    db.commit()
    Unit
  }

  private[this] def applyObjectAddPropertyOperation(fqn: ModelFqn, operation: AppliedObjectAddPropertyOperation, db: ODatabaseDocumentTx): Unit = {
    val value = OrientDataValueBuilder.dataValueToODocument(operation.value, getModelRid(fqn, db))
    value.save()
    db.commit()

    val queryString =
      s"""UPDATE ObjectValue PUT
             |  children = :property, :value
             |WHERE
             |  id = :id AND
             |  model.collection.id = :collectionId AND
             |  model.id = :modelId""".stripMargin
    val updateCommand = new OCommandSQL(queryString)
    val params = Map(
      Id -> operation.id,
      CollectionId -> fqn.collectionId,
      ModelId -> fqn.modelId,
      Value -> value,
      "property" -> operation.property)
    db.command(updateCommand).execute(params.asJava)
    db.commit()
    ()
  }

  private[this] def applyObjectSetPropertyOperation(
    fqn: ModelFqn, operation: AppliedObjectSetPropertyOperation, db: ODatabaseDocumentTx): Unit = {
    val value = OrientDataValueBuilder.dataValueToODocument(operation.value, getModelRid(fqn, db))
    value.save()
    db.commit()

    val queryString =
      s"""UPDATE ObjectValue PUT
             |  children = :property, :value
             |WHERE
             |  id = :id AND
             |  model.collection.id = :collectionId AND
             |  model.id = :modelId""".stripMargin
    val updateCommand = new OCommandSQL(queryString)
    val params = Map(
      Id -> operation.id,
      CollectionId -> fqn.collectionId,
      ModelId -> fqn.modelId,
      Value -> value,
      "property" -> operation.property)
    db.command(updateCommand).execute(params.asJava)
    db.commit()
    ()
  }

  private[this] def applyObjectRemovePropertyOperation(fqn: ModelFqn, operation: AppliedObjectRemovePropertyOperation, db: ODatabaseDocumentTx): Unit = {
    val queryString =
      s"""UPDATE ObjectValue REMOVE
         |  children = :property
         |WHERE
         |  id = :id AND
         |  model.collection.id = :collectionId AND
         |  model.id = :modelId""".stripMargin
    val updateCommand = new OCommandSQL(queryString)
    val params = Map(
      Id -> operation.id,
      CollectionId -> fqn.collectionId,
      ModelId -> fqn.modelId,
      "property" -> operation.property)
    db.command(updateCommand).execute(params.asJava)
    db.commit()
  }

  private[this] def applyObjectSetOperation(fqn: ModelFqn, operation: AppliedObjectSetOperation, db: ODatabaseDocumentTx): Unit = {
    val children = operation.value map { case (k, v) => (k, OrientDataValueBuilder.dataValueToODocument(v, getModelRid(fqn, db))) }
    children.values foreach { child => child.save() }
    db.commit()

    val queryString =
      s"""UPDATE ObjectValue SET
             |  children = :value
             |WHERE
             |  id = :id AND
             |  model.collection.id = :collectionId AND
             |  model.id = :modelId""".stripMargin
    val updateCommand = new OCommandSQL(queryString)
    val params = Map(
      Id -> operation.id,
      CollectionId -> fqn.collectionId,
      ModelId -> fqn.modelId,
      Value -> children.asJava)
    db.command(updateCommand).execute(params.asJava)
    db.commit()
    ()
  }

  private[this] def applyStringInsertOperation(fqn: ModelFqn, operation: AppliedStringInsertOperation, db: ODatabaseDocumentTx): Unit = {
    val queryString =
      s"""UPDATE StringValue SET
         |  value = value.left(:index).append(:value).append(value.substring(:index))
         |WHERE
         |  id = :id AND
         |  model.collection.id = :collectionId AND
         |  model.id = :modelId""".stripMargin

    // FIXME remove this when the following orient issue is resolved
    // https://github.com/orientechnologies/orientdb/issues/6250
    val hackValue = if (OIOUtils.isStringContent(operation.value)) {
      val hack = "\"\"" + operation.value + "\"\""
      logger.warn(s"Using OrientDB Hack for string append: ${operation.value} -> ${hack}")
      hack
    } else {
      operation.value
    }

    val updateCommand = new OCommandSQL(queryString)
    val params = Map(
      Id -> operation.id,
      CollectionId -> fqn.collectionId,
      ModelId -> fqn.modelId,
      Index -> operation.index,
      Value -> hackValue)
    db.command(updateCommand).execute(params.asJava)
    db.commit()
    ()
  }

  private[this] def applyStringRemoveOperation(fqn: ModelFqn, operation: AppliedStringRemoveOperation, db: ODatabaseDocumentTx): Unit = {
    val endLength = operation.index + operation.length
    val queryString =
      s"""UPDATE StringValue SET
         |  value = value.left(:index).append(value.substring(:endLength))
         |WHERE
         |  id = :id AND
         |  model.collection.id = :collectionId AND
         |  model.id = :modelId""".stripMargin
    val updateCommand = new OCommandSQL(queryString)
    val params = Map(
      Id -> operation.id,
      CollectionId -> fqn.collectionId,
      ModelId -> fqn.modelId,
      Index -> operation.index,
      "endLength" -> endLength)
    db.command(updateCommand).execute(params.asJava)
    db.commit()
  }

  private[this] def applyStringSetOperation(fqn: ModelFqn, operation: AppliedStringSetOperation, db: ODatabaseDocumentTx): Unit = {
    val queryString =
      s"""UPDATE StringValue SET
         |  value = :value
         |WHERE
         |  id = :id AND
         |  model.collection.id = :collectionId AND
         |  model.id = :modelId""".stripMargin
    val updateCommand = new OCommandSQL(queryString)
    val params = Map(
      Id -> operation.id,
      CollectionId -> fqn.collectionId,
      ModelId -> fqn.modelId,
      Value -> operation.value)
    db.command(updateCommand).execute(params.asJava)
    db.commit()
  }

  private[this] def applyNumberAddOperation(fqn: ModelFqn, operation: AppliedNumberAddOperation, db: ODatabaseDocumentTx): Unit = {
    val value = operation.value
    val queryString =
      s"""UPDATE DoubleValue SET
         |  value = eval('value + $value')
         |WHERE
         |  id = :id AND
         |  model.collection.id = :collectionId AND
         |  model.id = :modelId""".stripMargin
    val updateCommand = new OCommandSQL(queryString)
    val params = Map(
      Id -> operation.id,
      CollectionId -> fqn.collectionId,
      ModelId -> fqn.modelId)
    db.command(updateCommand).execute(params.asJava)
  }

  private[this] def applyNumberSetOperation(fqn: ModelFqn, operation: AppliedNumberSetOperation, db: ODatabaseDocumentTx): Unit = {
    val queryString =
      s"""UPDATE DoubleValue SET
         |  value = :value
         |WHERE
         |  id = :id AND
         |  model.collection.id = :collectionId AND
         |  model.id = :modelId""".stripMargin
    val updateCommand = new OCommandSQL(queryString)
    val params = Map(
      Id -> operation.id,
      CollectionId -> fqn.collectionId,
      ModelId -> fqn.modelId,
      Value -> operation.value)
    db.command(updateCommand).execute(params.asJava)
  }

  private[this] def applyBooleanSetOperation(fqn: ModelFqn, operation: AppliedBooleanSetOperation, db: ODatabaseDocumentTx): Unit = {
    val queryString =
      s"""UPDATE BooleanValue SET
         |  value = :value
         |WHERE
         |  id = :id AND
         |  model.collection.id = :collectionId AND
         |  model.id = :modelId""".stripMargin
    val updateCommand = new OCommandSQL(queryString)
    val params = Map(
      Id -> operation.id,
      CollectionId -> fqn.collectionId,
      ModelId -> fqn.modelId,
      Value -> operation.value)
    db.command(updateCommand).execute(params.asJava)
  }
  
  private[this] def applyDateSetOperation(fqn: ModelFqn, operation: AppliedDateSetOperation, db: ODatabaseDocumentTx): Unit = {
    val queryString =
      s"""UPDATE DateValue SET
         |  value = :value
         |WHERE
         |  id = :id AND
         |  model.collection.id = :collectionId AND
         |  model.id = :modelId""".stripMargin
    val updateCommand = new OCommandSQL(queryString)
    val params = Map(
      Id -> operation.id,
      CollectionId -> fqn.collectionId,
      ModelId -> fqn.modelId,
      Value -> Date.from(operation.value))
    db.command(updateCommand).execute(params.asJava)
  }

  private[this] def getModelRid(fqn: ModelFqn, db: ODatabaseDocumentTx): ORID = {
    ModelStore.getModelRid(fqn.modelId, db).get
  }
}
