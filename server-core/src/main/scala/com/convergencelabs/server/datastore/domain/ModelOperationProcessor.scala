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
import com.orientechnologies.orient.core.db.document.ODatabaseDocument
import com.orientechnologies.orient.core.id.ORID

import grizzled.slf4j.Logging
import com.convergencelabs.server.datastore.OrientDBUtil
import com.convergencelabs.server.datastore.AbstractDatabasePersistence
import scala.util.Success

object ModelOperationProcessor {
  sealed trait DataValueDeleteStrategy
  case class DeleteObjectKey(key: String) extends DataValueDeleteStrategy
  case class DeleteArrayIndex(index: Int) extends DataValueDeleteStrategy
  case object DeleteAllChildren extends DataValueDeleteStrategy
}

class ModelOperationProcessor private[domain] (
  private[this] val dbProvider: DatabaseProvider,
  private[this] val modelOpStore: ModelOperationStore,
  private[this] val modelStore: ModelStore)
  extends AbstractDatabasePersistence(dbProvider)
  with Logging {

  import ModelOperationProcessor._

  val Id = "id";
  val CollectionId = "collectionId"
  val ModelId = "modelId"
  val Value = "value"
  val Index = "index"

  def processModelOperation(modelOperation: NewModelOperation): Try[Unit] = tryWithDb { db =>
    // TODO this should all be in a transaction, but OrientDB has a problem with this.

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
  private[this] def applyOperationToModel(modelId: String, operation: AppliedOperation, db: ODatabaseDocument): Try[Unit] = {
    operation match {
      case op: AppliedCompoundOperation =>
        // Apply all operations in the compound op.
        // FIXME this should happen in a transaction
        Try(op.operations foreach { o => applyOperationToModel(modelId, o, db).get })

      case op: AppliedDiscreteOperation if op.noOp =>
        // Do nothing since this is a noOp
        Success(())

      // Actual Operation Handling

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

  //
  // Array Operations
  //
  private[this] def applyArrayInsertOperation(modelId: String, operation: AppliedArrayInsertOperation, db: ODatabaseDocument): Try[Unit] = {
    Try {
      val value = OrientDataValueBuilder.dataValueToODocument(operation.value, getModelRid(modelId, db))
      value.save()
      value
    } flatMap { value =>
      val script = createUpdate("SET children = arrayInsert(children, :index, :value)", None)
      val params = Map(Id -> operation.id, ModelId -> modelId, Index -> operation.index, Value -> value)
      OrientDBUtil.mutateOneDocumentWithScript(db, script, params)
    }
  }

  private[this] def applyArrayRemoveOperation(
    modelId: String, operation: AppliedArrayRemoveOperation, db: ODatabaseDocument): Try[Unit] = {

    val script = createUpdate("SET children = arrayRemove(children, :index)", Some(DeleteArrayIndex(operation.index)))
    val params = Map(Id -> operation.id, ModelId -> modelId, Index -> operation.index)
    OrientDBUtil.mutateOneDocumentWithScript(db, script, params)
  }

  private[this] def applyArrayReplaceOperation(
    modelId: String, operation: AppliedArrayReplaceOperation, db: ODatabaseDocument): Try[Unit] = {
    Try {
      val value = OrientDataValueBuilder.dataValueToODocument(operation.value, getModelRid(modelId, db))
      value.save()
      db.commit()
      value
    } flatMap { value =>
      val script = createUpdate("SET children = arrayReplace(children, :index, :value)", Some(DeleteArrayIndex(operation.index)))
      val params = Map(Id -> operation.id, ModelId -> modelId, Index -> operation.index, Value -> value)
      OrientDBUtil.mutateOneDocumentWithScript(db, script, params)
    }
  }

  private[this] def applyArrayMoveOperation(modelId: String, operation: AppliedArrayMoveOperation, db: ODatabaseDocument): Try[Unit] = {
    val script = createUpdate("SET children = arrayMove(children, :fromIndex, :toIndex)", None)
    val params = Map(Id -> operation.id, ModelId -> modelId, "fromIndex" -> operation.fromIndex, "toIndex" -> operation.toIndex)
    OrientDBUtil.mutateOneDocumentWithScript(db, script, params)
  }

  private[this] def applyArraySetOperation(modelId: String, operation: AppliedArraySetOperation, db: ODatabaseDocument): Try[Unit] = {
    Try {
      val children = operation.value map (v => OrientDataValueBuilder.dataValueToODocument(v, getModelRid(modelId, db)))
      children.foreach { child => child.save() }
      db.commit()
      children
    } flatMap { children =>
      val script = createUpdate("SET children = :value", Some(DeleteAllChildren))
      val params = Map(Id -> operation.id, ModelId -> modelId, Value -> children.asJava)

      OrientDBUtil
        .mutateOneDocumentWithScript(db, script, params)
        .flatMap(_ => Try(db.commit()))
    }
  }

  //
  // Object Operations
  //
  private[this] def applyObjectAddPropertyOperation(modelId: String, operation: AppliedObjectAddPropertyOperation, db: ODatabaseDocument): Try[Unit] = {
    Try {
      val value = OrientDataValueBuilder.dataValueToODocument(operation.value, getModelRid(modelId, db))
      value.save()
      db.commit()
      value
    } flatMap { value =>

      val script = createUpdate("PUT children = :property, :value", None)
      val params = Map(Id -> operation.id, ModelId -> modelId, Value -> value, "property" -> operation.property)
      OrientDBUtil.mutateOneDocumentWithScript(db, script, params)
    }
  }

  private[this] def applyObjectSetPropertyOperation(
    modelId: String, operation: AppliedObjectSetPropertyOperation, db: ODatabaseDocument): Try[Unit] = {
    Try {
      val value = OrientDataValueBuilder.dataValueToODocument(operation.value, getModelRid(modelId, db))
      value.save()
      db.commit()
      value
    } flatMap { value =>
      val script = createUpdate("PUT children = :property, :value", Some(DeleteObjectKey(operation.property)))
      val params = Map(Id -> operation.id, ModelId -> modelId, Value -> value, "property" -> operation.property)
      OrientDBUtil.mutateOneDocumentWithScript(db, script, params)
    }
  }

  private[this] def applyObjectRemovePropertyOperation(modelId: String, operation: AppliedObjectRemovePropertyOperation, db: ODatabaseDocument): Try[Unit] = {
    val script = createUpdate("REMOVE children = :property", Some(DeleteObjectKey(operation.property)))
    val params = Map(Id -> operation.id, ModelId -> modelId, "property" -> operation.property)
    OrientDBUtil.mutateOneDocumentWithScript(db, script, params)
  }

  private[this] def applyObjectSetOperation(modelId: String, operation: AppliedObjectSetOperation, db: ODatabaseDocument): Try[Unit] = {
    Try {
      val children = operation.value map { case (k, v) => (k, OrientDataValueBuilder.dataValueToODocument(v, getModelRid(modelId, db))) }
      children.values foreach { child => child.save() }
      db.commit()
      children
    } flatMap { children =>
      val script = createUpdate("SET children = :value", Some(DeleteAllChildren))
      val params = Map(Id -> operation.id, ModelId -> modelId, Value -> children.asJava)
      OrientDBUtil.mutateOneDocumentWithScript(db, script, params)
    }
  }

  //
  // String Operations
  //
  private[this] def applyStringInsertOperation(modelId: String, operation: AppliedStringInsertOperation, db: ODatabaseDocument): Try[Unit] = {
    // FIXME remove this when the following orient issue is resolved
    // https://github.com/orientechnologies/orientdb/issues/6250
    val hackValue = if (OIOUtils.isStringContent(operation.value)) {
      val hack = "\"\"" + operation.value + "\"\""
      logger.warn(s"Using OrientDB Hack for string append: ${operation.value} -> ${hack}")
      hack
    } else {
      operation.value
    }

    val script = createUpdate("SET value = value.left(:index).append(:value).append(value.substring(:index))", None)
    val params = Map(Id -> operation.id, ModelId -> modelId, Index -> operation.index, Value -> hackValue)
    OrientDBUtil.mutateOneDocumentWithScript(db, script, params)
  }

  private[this] def applyStringRemoveOperation(modelId: String, operation: AppliedStringRemoveOperation, db: ODatabaseDocument): Try[Unit] = {
    val script = createUpdate("SET value = value.left(:index).append(value.substring(:endLength))", None)
    val endLength = operation.index + operation.length
    val params = Map(Id -> operation.id, ModelId -> modelId, Index -> operation.index, "endLength" -> endLength)
    OrientDBUtil.mutateOneDocumentWithScript(db, script, params)
  }

  private[this] def applyStringSetOperation(modelId: String, operation: AppliedStringSetOperation, db: ODatabaseDocument): Try[Unit] = {
    val script = createUpdate("SET value = :value", None)
    val params = Map(Id -> operation.id, ModelId -> modelId, Value -> operation.value)
    OrientDBUtil.mutateOneDocumentWithScript(db, script, params)
  }

  //
  // Number Operations
  //
  private[this] def applyNumberAddOperation(modelId: String, operation: AppliedNumberAddOperation, db: ODatabaseDocument): Try[Unit] = {
    val value = operation.value
    val script = createUpdate(s"SET value = eval('value + $value')", None)
    val params = Map(Id -> operation.id, ModelId -> modelId)
    OrientDBUtil.mutateOneDocumentWithScript(db, script, params)
  }

  private[this] def applyNumberSetOperation(modelId: String, operation: AppliedNumberSetOperation, db: ODatabaseDocument): Try[Unit] = {
    val script = createUpdate("SET value = :value", None)
    val params = Map(Id -> operation.id, ModelId -> modelId, Value -> operation.value)
    OrientDBUtil.mutateOneDocumentWithScript(db, script, params)
  }

  //
  // Boolean Operations
  //
  private[this] def applyBooleanSetOperation(modelId: String, operation: AppliedBooleanSetOperation, db: ODatabaseDocument): Try[Unit] = {
    val script = createUpdate("SET value = :value", None)
    val params = Map(Id -> operation.id, ModelId -> modelId, Value -> operation.value)
    OrientDBUtil.mutateOneDocumentWithScript(db, script, params)
  }

  //
  // Date Operations
  //
  private[this] def applyDateSetOperation(modelId: String, operation: AppliedDateSetOperation, db: ODatabaseDocument): Try[Unit] = {
    val script = createUpdate("SET value = :value", None)
    val params = Map(Id -> operation.id, ModelId -> modelId, Value -> Date.from(operation.value))
    OrientDBUtil.mutateOneDocumentWithScript(db, script, params)
  }

  private[this] def createUpdate(updateClause: String, deleteStrategy: Option[DataValueDeleteStrategy]): String = {
    // In Orient DB 2.x you can't create an index that spans classes. So we can't have
    // an index that would speed up getting the data value id, and a model id, e.g.
    // WHERE id = :id AND model.id = :modelId. Therefore we do this in two steps, looking
    // up the model first, from it's index, and then the DataValue next using the id and
    // model fields on the data value class itself, which is indexed.

    val (childrenSelector, deleteCommand) = deleteStrategy map {
      case DeleteAllChildren =>
        "children"
      case DeleteObjectKey(childPath) =>
        s"children[`${childPath}`]"
      case DeleteArrayIndex(index) =>
        s"children[${index}]"
    } map { path =>
      (
        s"LET childrenToDelete = SELECT expand($path) FROM $$dv;",
        "DELETE FROM (TRAVERSE children FROM $childrenToDelete);")
    } getOrElse (("", ""))

    s"""
      |BEGIN;
      |LET models = SELECT rid FROM index:Model.id WHERE key = :modelId;
      |LET model = $$models[0].rid;
      |LET dvs = SELECT @rid FROM DataValue WHERE id =:id AND model = $$model;
      |LET dv = $$dvs[0].rid;
      |${childrenSelector}
      |${deleteCommand}
      |UPDATE $$dv ${updateClause};
      |COMMIT;""".stripMargin
  }

  private[this] def getModelRid(modelId: String, db: ODatabaseDocument): ORID = {
    ModelStore.getModelRid(modelId, db).get
  }
}
