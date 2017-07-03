package com.convergencelabs.server.datastore.domain

import java.util.Date

import scala.collection.JavaConverters.mapAsJavaMapConverter
import scala.collection.JavaConverters.seqAsJavaListConverter
import scala.util.Try

import com.convergencelabs.server.datastore.AbstractDatabasePersistence
import com.convergencelabs.server.datastore.DatabaseProvider
import com.convergencelabs.server.domain.model.NewModelOperation
import com.convergencelabs.server.domain.model.ot.AppliedArrayInsertOperation
import com.convergencelabs.server.domain.model.ot.AppliedArrayMoveOperation
import com.convergencelabs.server.domain.model.ot.AppliedArrayRemoveOperation
import com.convergencelabs.server.domain.model.ot.AppliedArrayReplaceOperation
import com.convergencelabs.server.domain.model.ot.AppliedArraySetOperation
import com.convergencelabs.server.domain.model.ot.AppliedBooleanSetOperation
import com.convergencelabs.server.domain.model.ot.AppliedCompoundOperation
import com.convergencelabs.server.domain.model.ot.AppliedDateSetOperation
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
import com.orientechnologies.orient.core.command.script.OCommandScript
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx
import com.orientechnologies.orient.core.id.ORID
import com.orientechnologies.orient.core.sql.OCommandSQL

import grizzled.slf4j.Logging
import com.convergencelabs.server.datastore.QueryUtil

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
    applyOperationToModel(modelOperation.modelId, modelOperation.op, db).flatMap { _ =>
      // Persist the operation
      modelOpStore.createModelOperation(modelOperation)
    }.flatMap { _ =>
      // Update the model metadata
      modelStore.updateModelOnOperation(modelOperation.modelId, modelOperation.timestamp)
    }.get

  }

  // scalastyle:off cyclomatic.complexity
  private[this] def applyOperationToModel(modelId: String, operation: AppliedOperation, db: ODatabaseDocumentTx): Try[Unit] = Try {
    operation match {
      case op: AppliedCompoundOperation => op.operations foreach { o => applyOperationToModel(modelId, o, db) }
      case op: AppliedDiscreteOperation if op.noOp => // Do nothing since this is a noOp

      case op: AppliedArrayInsertOperation => applyArrayInsertOperation(modelId, op, db)
      case op: AppliedArrayRemoveOperation => applyArrayRemoveOperation(modelId, op, db)
      case op: AppliedArrayReplaceOperation => applyArrayReplaceOperation(modelId, op, db)
      case op: AppliedArrayMoveOperation => applyArrayMoveOperation(modelId, op, db)
      case op: AppliedArraySetOperation => applyArraySetOperation(modelId, op, db)

      case op: AppliedObjectAddPropertyOperation => applyObjectAddPropertyOperation(modelId, op, db)
      case op: AppliedObjectSetPropertyOperation => applyObjectSetPropertyOperation(modelId, op, db)
      case op: AppliedObjectRemovePropertyOperation => applyObjectRemovePropertyOperation(modelId, op, db)
      case op: AppliedObjectSetOperation => applyObjectSetOperation(modelId, op, db)

      case op: AppliedStringInsertOperation => applyStringInsertOperation(modelId, op, db)
      case op: AppliedStringRemoveOperation => applyStringRemoveOperation(modelId, op, db)
      case op: AppliedStringSetOperation => applyStringSetOperation(modelId, op, db)

      case op: AppliedNumberAddOperation => applyNumberAddOperation(modelId, op, db)
      case op: AppliedNumberSetOperation => applyNumberSetOperation(modelId, op, db)

      case op: AppliedBooleanSetOperation => applyBooleanSetOperation(modelId, op, db)

      case op: AppliedDateSetOperation => applyDateSetOperation(modelId, op, db)
    }
  }
  // scalastyle:on cyclomatic.complexity

  private[this] def applyArrayInsertOperation(modelId: String, operation: AppliedArrayInsertOperation, db: ODatabaseDocumentTx): Unit = {
    val value = OrientDataValueBuilder.dataValueToODocument(operation.value, getModelRid(modelId, db))
    value.save()

    val queryString =
      s"""UPDATE ArrayValue SET
             |  children = arrayInsert(children, :index, :value)
             |WHERE
             |  id = :id AND
             |  model.id = :modelId""".stripMargin
    val updateCommand = new OCommandSQL(queryString)
    val params = Map(
      Id -> operation.id,
      ModelId -> modelId,
      Index -> operation.index,
      Value -> value)
    db.command(updateCommand).execute(params.asJava)
    ()
  }

  private[this] def applyArrayRemoveOperation(
    modelId: String, operation: AppliedArrayRemoveOperation, db: ODatabaseDocumentTx): Unit = {
    val queryString =
      s"""UPDATE ArrayValue SET
         |  children = arrayRemove(children, :index)
         |WHERE
         |  id = :id AND
         |  model.id = :modelId""".stripMargin
    val updateCommand = new OCommandSQL(queryString)
    val params = Map(
      Id -> operation.id,
      ModelId -> modelId,
      Index -> operation.index)
    db.command(updateCommand).execute(params.asJava)
    db.commit()
  }

  private[this] def applyArrayReplaceOperation(
    modelId: String, operation: AppliedArrayReplaceOperation, db: ODatabaseDocumentTx): Unit = {
    val value = OrientDataValueBuilder.dataValueToODocument(operation.value, getModelRid(modelId, db))
    value.save()
    db.commit()

    val queryString =
      s"""UPDATE ArrayValue SET
             |  children = arrayReplace(children, :index, :value)
             |WHERE
             |  model.id = :modelId""".stripMargin
    val updateCommand = new OCommandSQL(queryString)

    val params = Map(
      Id -> operation.id,
      ModelId -> modelId,
      Index -> operation.index,
      Value -> value)
    db.command(updateCommand).execute(params.asJava)
    ()
  }

  private[this] def applyArrayMoveOperation(modelId: String, operation: AppliedArrayMoveOperation, db: ODatabaseDocumentTx): Unit = {
    val queryString =
      s"""UPDATE ArrayValue SET
         |  children = arrayMove(children, :fromIndex, :toIndex)
         |WHERE
         |  id = :id AND
         |  model.id = :modelId""".stripMargin
    val updateCommand = new OCommandSQL(queryString)
    val params = Map(
      Id -> operation.id,
      ModelId -> modelId,
      "fromIndex" -> operation.fromIndex,
      "toIndex" -> operation.toIndex)
    db.command(updateCommand).execute(params.asJava)
    db.commit()
    ()
  }

  private[this] def applyArraySetOperation(modelId: String, operation: AppliedArraySetOperation, db: ODatabaseDocumentTx): Unit = {
    val children = operation.value map (v => OrientDataValueBuilder.dataValueToODocument(v, getModelRid(modelId, db)))
    children.foreach { child => child.save() }
    db.commit()

    val script =
      """BEGIN;
        |LET model = SELECT rid FROM index:Model.id WHERE key = :modelId;
        |UPDATE ArrayValue SET children = :value WHERE id = :id AND model = first($model).rid;
        |COMMIT;""".stripMargin
    val params = Map(Id -> operation.id, ModelId -> modelId, Value -> children.asJava)
    
    QueryUtil.updateSingleDocWithScript(script, params, db).get
    db.commit()
    ()
  }

  private[this] def applyObjectAddPropertyOperation(modelId: String, operation: AppliedObjectAddPropertyOperation, db: ODatabaseDocumentTx): Unit = {
    val value = OrientDataValueBuilder.dataValueToODocument(operation.value, getModelRid(modelId, db))
    value.save()
    db.commit()

    val queryString =
      s"""UPDATE ObjectValue PUT
             |  children = :property, :value
             |WHERE
             |  id = :id AND
             |  model.id = :modelId""".stripMargin
    val updateCommand = new OCommandSQL(queryString)
    val params = Map(
      Id -> operation.id,
      ModelId -> modelId,
      Value -> value,
      "property" -> operation.property)
    db.command(updateCommand).execute(params.asJava)
    db.commit()
    ()
  }

  private[this] def applyObjectSetPropertyOperation(
    modelId: String, operation: AppliedObjectSetPropertyOperation, db: ODatabaseDocumentTx): Unit = {
    val value = OrientDataValueBuilder.dataValueToODocument(operation.value, getModelRid(modelId, db))
    value.save()
    db.commit()

    val queryString =
      s"""UPDATE ObjectValue PUT
             |  children = :property, :value
             |WHERE
             |  id = :id AND
             |  model.id = :modelId""".stripMargin
    val updateCommand = new OCommandSQL(queryString)
    val params = Map(
      Id -> operation.id,
      ModelId -> modelId,
      Value -> value,
      "property" -> operation.property)
    db.command(updateCommand).execute(params.asJava)
    db.commit()
    ()
  }

  private[this] def applyObjectRemovePropertyOperation(modelId: String, operation: AppliedObjectRemovePropertyOperation, db: ODatabaseDocumentTx): Unit = {
    val queryString =
      s"""UPDATE ObjectValue REMOVE
         |  children = :property
         |WHERE
         |  id = :id AND
         |  model.id = :modelId""".stripMargin
    val updateCommand = new OCommandSQL(queryString)
    val params = Map(
      Id -> operation.id,
      ModelId -> modelId,
      "property" -> operation.property)
    db.command(updateCommand).execute(params.asJava)
    db.commit()
  }

  private[this] def applyObjectSetOperation(modelId: String, operation: AppliedObjectSetOperation, db: ODatabaseDocumentTx): Unit = {
    val children = operation.value map { case (k, v) => (k, OrientDataValueBuilder.dataValueToODocument(v, getModelRid(modelId, db))) }
    children.values foreach { child => child.save() }
    db.commit()

    val queryString =
      s"""UPDATE ObjectValue SET
             |  children = :value
             |WHERE
             |  id = :id AND
             |  model.id = :modelId""".stripMargin
    val updateCommand = new OCommandSQL(queryString)
    val params = Map(
      Id -> operation.id,
      ModelId -> modelId,
      Value -> children.asJava)
    db.command(updateCommand).execute(params.asJava)
    db.commit()
    ()
  }

  private[this] def applyStringInsertOperation(modelId: String, operation: AppliedStringInsertOperation, db: ODatabaseDocumentTx): Unit = {
    val queryString =
      s"""UPDATE StringValue SET
         |  value = value.left(:index).append(:value).append(value.substring(:index))
         |WHERE
         |  id = :id AND
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
      ModelId -> modelId,
      Index -> operation.index,
      Value -> hackValue)
    db.command(updateCommand).execute(params.asJava)
    db.commit()
    ()
  }

  private[this] def applyStringRemoveOperation(modelId: String, operation: AppliedStringRemoveOperation, db: ODatabaseDocumentTx): Unit = {
    val endLength = operation.index + operation.length
    val queryString =
      s"""UPDATE StringValue SET
         |  value = value.left(:index).append(value.substring(:endLength))
         |WHERE
         |  id = :id AND
         |  model.id = :modelId""".stripMargin
    val updateCommand = new OCommandSQL(queryString)
    val params = Map(
      Id -> operation.id,
      ModelId -> modelId,
      Index -> operation.index,
      "endLength" -> endLength)
    db.command(updateCommand).execute(params.asJava)
    db.commit()
  }

  private[this] def applyStringSetOperation(modelId: String, operation: AppliedStringSetOperation, db: ODatabaseDocumentTx): Unit = {
    val queryString =
      s"""UPDATE StringValue SET
         |  value = :value
         |WHERE
         |  id = :id AND
         |  model.id = :modelId""".stripMargin
    val updateCommand = new OCommandSQL(queryString)
    val params = Map(
      Id -> operation.id,
      ModelId -> modelId,
      Value -> operation.value)
    db.command(updateCommand).execute(params.asJava)
    db.commit()
  }

  private[this] def applyNumberAddOperation(modelId: String, operation: AppliedNumberAddOperation, db: ODatabaseDocumentTx): Unit = {
    val value = operation.value
    val queryString =
      s"""UPDATE DoubleValue SET
         |  value = eval('value + $value')
         |WHERE
         |  id = :id AND
         |  model.id = :modelId""".stripMargin
    val updateCommand = new OCommandSQL(queryString)
    val params = Map(
      Id -> operation.id,
      ModelId -> modelId)
    db.command(updateCommand).execute(params.asJava)
  }

  private[this] def applyNumberSetOperation(modelId: String, operation: AppliedNumberSetOperation, db: ODatabaseDocumentTx): Unit = {
    val queryString =
      s"""UPDATE DoubleValue SET
         |  value = :value
         |WHERE
         |  id = :id AND
         |  model.id = :modelId""".stripMargin
    val updateCommand = new OCommandSQL(queryString)
    val params = Map(
      Id -> operation.id,
      ModelId -> modelId,
      Value -> operation.value)
    db.command(updateCommand).execute(params.asJava)
  }

  private[this] def applyBooleanSetOperation(modelId: String, operation: AppliedBooleanSetOperation, db: ODatabaseDocumentTx): Unit = {
    val queryString =
      s"""UPDATE BooleanValue SET
         |  value = :value
         |WHERE
         |  id = :id AND
         |  model.id = :modelId""".stripMargin
    val updateCommand = new OCommandSQL(queryString)
    val params = Map(
      Id -> operation.id,
      ModelId -> modelId,
      Value -> operation.value)
    db.command(updateCommand).execute(params.asJava)
  }

  private[this] def applyDateSetOperation(modelId: String, operation: AppliedDateSetOperation, db: ODatabaseDocumentTx): Unit = {
    val queryString =
      s"""UPDATE DateValue SET
         |  value = :value
         |WHERE
         |  id = :id AND
         |  model.id = :modelId""".stripMargin
    val updateCommand = new OCommandSQL(queryString)
    val params = Map(
      Id -> operation.id,
      ModelId -> modelId,
      Value -> Date.from(operation.value))
    db.command(updateCommand).execute(params.asJava)
  }

  private[this] def getModelRid(modelId: String, db: ODatabaseDocumentTx): ORID = {
    ModelStore.getModelRid(modelId, db).get
  }
}
