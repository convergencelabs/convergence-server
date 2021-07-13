/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is part of the Convergence Server, which is released under
 * the terms of the GNU General Public License version 3 (GPLv3). A copy
 * of the GPLv3 should have been provided along with this file, typically
 * located in the "LICENSE" file, which is part of this source code package.
 * Alternatively, see <https://www.gnu.org/licenses/gpl-3.0.html> for the
 * full text of the GPLv3 license, if it was not provided.
 */

package com.convergencelabs.convergence.server.backend.datastore.domain.model

import com.convergencelabs.convergence.server.backend.datastore.{AbstractDatabasePersistence, OrientDBUtil}
import com.convergencelabs.convergence.server.backend.db.DatabaseProvider
import com.convergencelabs.convergence.server.backend.services.domain.model.NewModelOperation
import com.convergencelabs.convergence.server.backend.services.domain.model.ot._
import com.orientechnologies.orient.core.db.document.ODatabaseDocument
import com.orientechnologies.orient.core.id.ORID
import com.orientechnologies.orient.core.record.impl.ODocument
import grizzled.slf4j.Logging

import java.util.Date
import scala.jdk.CollectionConverters._
import scala.util.{Failure, Success, Try}

class ModelOperationProcessor private[domain](dbProvider: DatabaseProvider,
                                              modelOpStore: ModelOperationStore,
                                              modelStore: ModelStore)
  extends AbstractDatabasePersistence(dbProvider)
    with Logging {

  import ModelOperationProcessor._

  val Id = "id"
  val CollectionId = "collectionId"
  val ModelId = "modelId"
  val Value = "value"
  val Index = "index"

  def processModelOperation(modelOperation: NewModelOperation): Try[Unit] = withDb { db =>
    (for {
      // Begin Transaction
      // _ <- Try(db.begin())

      // 1. Apply the operation to the actual model.
      _ <- applyOperationToModel(modelOperation.modelId, modelOperation.op, db)

      // 2. Persist the operation to the Operation History
      _ <- modelOpStore.createModelOperation(modelOperation, Some(db))

      // 3. Update the model version and time stamp
      _ <- modelStore.updateModelOnOperation(modelOperation.modelId, modelOperation.version, modelOperation.timestamp, Some(db))

      // Commit Transaction
      // _ <- Try(db.commit())
    } yield {
      ()
    }).recoverWith {
      case cause: Throwable =>
        db.rollback()
        Failure(cause)
    }
  }

  // scalastyle:off cyclomatic.complexity
  private[this] def applyOperationToModel(modelId: String, operation: AppliedOperation, db: ODatabaseDocument): Try[Unit] = {
    operation match {
      case op: AppliedCompoundOperation =>
        // Apply all operations in the compound op.
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

      case op: AppliedStringSpliceOperation => applyStringSpliceOperation(modelId, op, db)
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
      OrientDBUtil.executeMutation(db, script, params).map(_ => ())
    }
  }

  private[this] def applyArrayRemoveOperation(
                                               modelId: String, operation: AppliedArrayRemoveOperation, db: ODatabaseDocument): Try[Unit] = {

    val script = createUpdate("SET children = arrayRemove(children, :index)", Some(DeleteArrayIndex(operation.index)))
    val params = Map(Id -> operation.id, ModelId -> modelId, Index -> operation.index)
    OrientDBUtil.executeMutation(db, script, params).map(_ => ())
  }

  private[this] def applyArrayReplaceOperation(
                                                modelId: String, operation: AppliedArrayReplaceOperation, db: ODatabaseDocument): Try[Unit] = {
    Try {
      val value = OrientDataValueBuilder.dataValueToODocument(operation.value, getModelRid(modelId, db))
      value.save()
      value
    } flatMap { value =>
      val script = createUpdate("SET children = arrayReplace(children, :index, :value)", Some(DeleteArrayIndex(operation.index)))
      val params = Map(Id -> operation.id, ModelId -> modelId, Index -> operation.index, Value -> value)
      OrientDBUtil.executeMutation(db, script, params).map(_ => ())
    }
  }

  private[this] def applyArrayMoveOperation(modelId: String, operation: AppliedArrayMoveOperation, db: ODatabaseDocument): Try[Unit] = {
    val script = createUpdate("SET children = arrayMove(children, :fromIndex, :toIndex)", None)
    val params = Map(Id -> operation.id, ModelId -> modelId, "fromIndex" -> operation.fromIndex, "toIndex" -> operation.toIndex)
    OrientDBUtil.executeMutation(db, script, params).map(_ => ())
  }

  private[this] def applyArraySetOperation(modelId: String, operation: AppliedArraySetOperation, db: ODatabaseDocument): Try[Unit] = {
    Try {
      if (operation.value.nonEmpty) {
        val modelRid = getModelRid(modelId, db)
        val children = operation.value map { v =>
          val doc = OrientDataValueBuilder.dataValueToODocument(v, modelRid)
          doc.save()
          doc
        }
        children
      } else {
        List()
      }
    } flatMap { children =>
      val script = createUpdate("SET children = :value", Some(DeleteAllArrayChildren))
      val params = Map(Id -> operation.id, ModelId -> modelId, Value -> children.asJava)

      OrientDBUtil
      OrientDBUtil.executeMutation(db, script, params).map(_ => ())
    }
  }

  //
  // Object Operations
  //
  private[this] def applyObjectAddPropertyOperation(modelId: String, operation: AppliedObjectAddPropertyOperation, db: ODatabaseDocument): Try[Unit] = {
    Try {
      val value = OrientDataValueBuilder.dataValueToODocument(operation.value, getModelRid(modelId, db))
      value.save()
      value
    } flatMap { value =>
      val script = createUpdate("SET children[:property] = :value", None)
      val params = Map(Id -> operation.id, ModelId -> modelId, Value -> value, "property" -> operation.property)
      OrientDBUtil.executeMutation(db, script, params).map(_ => ())
    }
  }

  private[this] def applyObjectSetPropertyOperation(modelId: String, operation: AppliedObjectSetPropertyOperation, db: ODatabaseDocument): Try[Unit] = {
    Try {
      val value = OrientDataValueBuilder.dataValueToODocument(operation.value, getModelRid(modelId, db))
      value.save()
      value
    } flatMap { value =>
      val script = createUpdate("SET children[:property] = :value", Some(DeleteObjectKey(operation.property)))
      val params = Map(Id -> operation.id, ModelId -> modelId, Value -> value, "property" -> operation.property)
      OrientDBUtil.executeMutation(db, script, params).map(_ => ())
    }
  }

  private[this] def applyObjectRemovePropertyOperation(modelId: String, operation: AppliedObjectRemovePropertyOperation, db: ODatabaseDocument): Try[Unit] = {
    // FIXME potential SQL injection due to string concatenation.
    val script = createUpdate(s"REMOVE children.`${operation.property}`", Some(DeleteObjectKey(operation.property)))
    val params = Map(Id -> operation.id, ModelId -> modelId, "property" -> operation.property)
    OrientDBUtil.executeMutation(db, script, params).map(_ => ())
  }

  private[this] def applyObjectSetOperation(modelId: String, operation: AppliedObjectSetOperation, db: ODatabaseDocument): Try[Unit] = {
    Try {
      if (operation.value.nonEmpty) {
        val modelRid = getModelRid(modelId, db)
        val children = operation.value map {
          case (k, v) =>
            val dvDoc = OrientDataValueBuilder.dataValueToODocument(v, modelRid)
            dvDoc.save()
            (k, dvDoc)
        }

        children
      } else {
        Map[String, ODocument]()
      }
    } flatMap { children =>
      val script = createUpdate("SET children = :value", Some(DeleteAllObjectChildren))
      val childMap = new java.util.HashMap[String, ODocument](children.asJava)
      val params = Map(Id -> operation.id, ModelId -> modelId, Value -> childMap)
      OrientDBUtil.executeMutation(db, script, params).map(_ => ())
    }
  }

  //
  // String Operations
  //

  private[this] def applyStringSpliceOperation(modelId: String, operation: AppliedStringSpliceOperation, db: ODatabaseDocument): Try[Unit] = {
    val script = createUpdate("SET value = value.left(:index).append(:insertedValue).append(value.substring(:endLength))", None)
    val endLength = operation.index + operation.deletedValue.getOrElse("").length
    val params = Map(
      Id -> operation.id,
      ModelId -> modelId,
      Index -> operation.index,
      "endLength" -> endLength,
      "insertedValue" -> operation.insertedValue)
    OrientDBUtil.executeMutation(db, script, params).map(_ => ())
  }

  private[this] def applyStringSetOperation(modelId: String, operation: AppliedStringSetOperation, db: ODatabaseDocument): Try[Unit] = {
    val script = createUpdate("SET value = :value", None)
    val params = Map(Id -> operation.id, ModelId -> modelId, Value -> operation.value)
    OrientDBUtil.executeMutation(db, script, params).map(_ => ())
  }

  //
  // Number Operations
  //
  private[this] def applyNumberAddOperation(modelId: String, operation: AppliedNumberAddOperation, db: ODatabaseDocument): Try[Unit] = {
    val value = operation.value
    val script = createUpdate(s"SET value = eval('value + $value')", None)
    val params = Map(Id -> operation.id, ModelId -> modelId)
    OrientDBUtil.executeMutation(db, script, params).map(_ => ())
  }

  private[this] def applyNumberSetOperation(modelId: String, operation: AppliedNumberSetOperation, db: ODatabaseDocument): Try[Unit] = {
    val script = createUpdate("SET value = :value", None)
    val params = Map(Id -> operation.id, ModelId -> modelId, Value -> operation.value)
    OrientDBUtil.executeMutation(db, script, params).map(_ => ())
  }

  //
  // Boolean Operations
  //
  private[this] def applyBooleanSetOperation(modelId: String, operation: AppliedBooleanSetOperation, db: ODatabaseDocument): Try[Unit] = {
    val script = createUpdate("SET value = :value", None)
    val params = Map(Id -> operation.id, ModelId -> modelId, Value -> operation.value)
    OrientDBUtil.executeMutation(db, script, params).map(_ => ())
  }

  //
  // Date Operations
  //
  private[this] def applyDateSetOperation(modelId: String, operation: AppliedDateSetOperation, db: ODatabaseDocument): Try[Unit] = {
    val script = createUpdate("SET value = :value", None)
    val params = Map(Id -> operation.id, ModelId -> modelId, Value -> Date.from(operation.value))
    OrientDBUtil.executeMutation(db, script, params).map(_ => ())
  }

  private[this] def createUpdate(updateClause: String, deleteStrategy: Option[DataValueDeleteStrategy]): String = {
    // In Orient DB 2.x you can't create an index that spans classes. So we can't have
    // an index that would speed up getting the data value id, and a model id, e.g.
    // WHERE id = :id AND model.id = :modelId. Therefore we do this in two steps, looking
    // up the model first, from it's index, and then the DataValue next using the id and
    // model fields on the data value class itself, which is indexed.

    val deleteCommand = deleteStrategy map {
      case DeleteAllArrayChildren =>
        "children"
      case DeleteAllObjectChildren =>
        "children.values()"
      case DeleteObjectKey(childPath) =>
        s"children.`$childPath`"
      case DeleteArrayIndex(index) =>
        s"children[$index]"
    } map { path =>
      // The TRAVERSE statement is overly complex because of these two bugs.
      // https://github.com/orientechnologies/orientdb/issues/9099
      // https://github.com/orientechnologies/orientdb/issues/9101
      // Ideally this would just be: TRAVERSE children FROM (SELECT expand($$directChildrenToDelete))
      s"""LET directChildrenToDelete = $$dv.$path;
         |LET allChildrenToDelete = SELECT FROM (TRAVERSE children, children.values() FROM (SELECT expand($$directChildrenToDelete))) WHERE @this INSTANCEOF "DataValue";
         |DELETE FROM (SELECT expand($$allChildrenToDelete));"""
    } getOrElse ""

    s"""|LET models = SELECT FROM index:Model.id WHERE key = :modelId;
        |LET model = $$models[0].rid;
        |LET dvs = SELECT FROM DataValue WHERE id = :id AND model = $$model;
        |LET dv = $$dvs[0];
        |$deleteCommand
        |UPDATE (SELECT expand($$dv)) $updateClause;""".stripMargin
  }

  private[this] def getModelRid(modelId: String, db: ODatabaseDocument): ORID = {
    ModelStore.getModelRid(modelId, db).get
  }
}

object ModelOperationProcessor {

  sealed trait DataValueDeleteStrategy

  final case class DeleteObjectKey(key: String) extends DataValueDeleteStrategy

  final case class DeleteArrayIndex(index: Int) extends DataValueDeleteStrategy

  case object DeleteAllArrayChildren extends DataValueDeleteStrategy

  case object DeleteAllObjectChildren extends DataValueDeleteStrategy

}