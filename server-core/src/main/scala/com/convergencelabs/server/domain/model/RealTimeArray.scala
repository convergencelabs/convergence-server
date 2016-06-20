package com.convergencelabs.server.domain.model

import com.convergencelabs.server.domain.model.ot.Operation
import com.convergencelabs.server.domain.model.ot.ObjectAddPropertyOperation
import com.convergencelabs.server.domain.model.ot.ObjectSetOperation
import com.convergencelabs.server.domain.model.ot.ObjectSetPropertyOperation
import com.convergencelabs.server.domain.model.ot.ObjectRemovePropertyOperation
import com.convergencelabs.server.domain.model.ot.ObjectRemovePropertyOperation
import com.convergencelabs.server.domain.model.ot.ObjectSetPropertyOperation
import com.convergencelabs.server.domain.model.ot.ArrayInsertOperation
import com.convergencelabs.server.domain.model.ot.DiscreteOperation
import com.convergencelabs.server.domain.model.ot.ArrayRemoveOperation
import com.convergencelabs.server.domain.model.ot.ArrayReplaceOperation
import com.convergencelabs.server.domain.model.ot.ArrayMoveOperation
import com.convergencelabs.server.domain.model.ot.ArraySetOperation
import com.convergencelabs.server.domain.model.ot.ArrayMoveOperation
import com.convergencelabs.server.domain.model.ot.ArrayReplaceOperation
import com.convergencelabs.server.domain.model.ot.ArrayRemoveOperation
import com.convergencelabs.server.domain.model.ot.ArrayInsertOperation
import com.convergencelabs.server.domain.model.ot.ArraySetOperation
import org.json4s.JsonAST.JArray
import scala.util.Try
import scala.util.Success
import scala.util.Failure
import com.convergencelabs.server.domain.model.data.ArrayValue

class RealTimeArray(
  private[this] val value: ArrayValue,
  private[this] val model: RealTimeModel,
  private[this] val parent: Option[RealTimeContainerValue],
  private[this] val parentField: Option[Any])
    extends RealTimeContainerValue(value.id, model, parent, parentField, List()) {

  var i = 0;
  var childValues = value.children.map {
    x => this.model.createValue(x, Some(this), Some({ i += 1; i }))
  }

  def children(): List[RealTimeValue] = {
    childValues.toList
  }

  def valueAt(path: List[Any]): Option[RealTimeValue] = {
    path match {
      case Nil =>
        Some(this)
      case (index: Int) :: Nil =>
        childValues.lift(index)
      case (index: Int) :: rest =>
        childValues.lift(index).flatMap {
          case child: RealTimeContainerValue => child.valueAt(rest)
          case _ => None
        }
      case _ =>
        None
    }
  }

  def data(): List[_] = {
    children.map({ v => v.data() })
  }

  protected def child(childPath: Any): Try[Option[RealTimeValue]] = {
    childPath match {
      case index: Int =>
        Success(childValues.lift(index))
      case _ =>
        Failure(new IllegalArgumentException("Child path must be a Int for a RealTimeArray"))
    }
  }

  def processOperation(op: DiscreteOperation): Try[Unit] = Try {
    op match {
      case insert: ArrayInsertOperation => processInsertOperation(insert)
      case remove: ArrayRemoveOperation => processRemoveOperation(remove)
      case replace: ArrayReplaceOperation => processReplaceOperation(replace)
      case reorder: ArrayMoveOperation => processReorderOperation(reorder)
      case value: ArraySetOperation => processSetValueOperation(value)
      case _ => throw new Error("Invalid operation for RealTimeObject")
    }
  }

  def processInsertOperation(op: ArrayInsertOperation): Unit = {
    val child = this.model.createValue(op.value, Some(this), Some(parentField))
    childValues = childValues.patch(op.index, List(child), 0)
    this.updateIndices(op.index + 1, childValues.length - 1)
  }

  def processRemoveOperation(op: ArrayRemoveOperation): Unit = {
    val oldChild = childValues(op.index)
    childValues = childValues.patch(op.index, List(), 1)
    this.updateIndices(op.index, childValues.length - 1)
  }

  def processReplaceOperation(op: ArrayReplaceOperation): Unit = {
    val oldChild = childValues(op.index)
    val child = this.model.createValue(op.value, Some(this), Some(parentField))
    childValues = childValues.patch(op.index, List(child), 1)
  }

  def processReorderOperation(op: ArrayMoveOperation): Unit = {
    val child = childValues(op.fromIndex)
    childValues = childValues.patch(op.fromIndex, List(), 1)
    childValues = childValues.patch(op.toIndex, List(child), 0)
    this.updateIndices(op.fromIndex, op.toIndex)
  }

  def processSetValueOperation(op: ArraySetOperation): Unit = {
    var i = 0;
    childValues = op.value.map {
      v => this.model.createValue(v, Some(this), Some({ i += 1; i }))
    }
  }

  private[this] def updateIndices(fromIndex: Int, toIndex: Int): Unit = {
    for {i <- fromIndex to toIndex} {
      childValues(i).parentField = Some(i)
    }
  }

  def detachChildren(): Unit = {
    childValues.foreach({ child => child.detach() })
  }
}
