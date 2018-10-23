package com.convergencelabs.server.datastore.domain

import java.util.Date

import scala.collection.JavaConverters.mapAsJavaMapConverter
import scala.collection.JavaConverters.seqAsJavaListConverter
import scala.util.Failure
import scala.util.Success
import scala.util.Try

import com.convergencelabs.server.datastore.AbstractDatabasePersistence
import com.convergencelabs.server.datastore.OrientDBUtil
import com.convergencelabs.server.db.DatabaseProvider
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
import com.orientechnologies.orient.core.record.impl.ODocument

import grizzled.slf4j.Logging

object ModelOperationProcessor {
  sealed trait DataValueDeleteStrategy
  case class DeleteObjectKey(key: String) extends DataValueDeleteStrategy
  case class DeleteArrayIndex(index: Int) extends DataValueDeleteStrategy
  case object DeleteAllArrayChildren extends DataValueDeleteStrategy
  case object DeleteAllObjectChildren extends DataValueDeleteStrategy
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

  def processModelOperation(modelOperation: NewModelOperation): Try[Unit] = withDb { db =>
    db.begin()
    (for {
      // 1. Apply the operation to the actual model.
      _ <- applyOperationToModel(modelOperation.modelId, modelOperation.op, db)
      
      // 2. Persist the operation to the Operation History
      _ <- modelOpStore.createModelOperation(modelOperation, Some(db))
      
      // 3. Update the model version and time stamp
      _ <- modelStore.updateModelOnOperation(modelOperation.modelId, modelOperation.version, modelOperation.timestamp, Some(db))
    } yield {
      db.commit()
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
      if (operation.value.length > 0) {
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

  private[this] def applyObjectSetPropertyOperation(
    modelId: String, operation: AppliedObjectSetPropertyOperation, db: ODatabaseDocument): Try[Unit] = {
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
      if (operation.value.size > 0) {
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
    val result = OrientDBUtil.executeMutation(db, script, params).map(_ => ())
    result
  }

  private[this] def applyStringRemoveOperation(modelId: String, operation: AppliedStringRemoveOperation, db: ODatabaseDocument): Try[Unit] = {
    val script = createUpdate("SET value = value.left(:index).append(value.substring(:endLength))", None)
    val endLength = operation.index + operation.length
    val params = Map(Id -> operation.id, ModelId -> modelId, Index -> operation.index, "endLength" -> endLength)
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
        s"children.`${childPath}`"
      case DeleteArrayIndex(index) =>
        s"children[${index}]"
    } map { path =>
      s"""LET directChildrenToDelete = SELECT expand($$dv.$path);           
         |LET allChildrenToDelete = TRAVERSE children FROM (SELECT expand($$directChildrenToDelete));
         |DELETE FROM (SELECT expand($$allChildrenToDelete));"""
    } getOrElse ("")

    s"""|LET models = SELECT FROM index:Model.id WHERE key = :modelId;
        |LET model = $$models[0].rid;
        |LET dvs = SELECT FROM DataValue WHERE id = :id AND model = $$model;
        |LET dv = $$dvs[0];
        |${deleteCommand}
        |UPDATE (SELECT expand($$dv)) ${updateClause};""".stripMargin
  }

  private[this] def getModelRid(modelId: String, db: ODatabaseDocument): ORID = {
    ModelStore.getModelRid(modelId, db).get
  }
}
