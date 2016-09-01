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
import com.convergencelabs.server.domain.model.ot.AppliedArrayOperation
import com.convergencelabs.server.domain.model.ot.AppliedArrayInsertOperation
import com.convergencelabs.server.domain.model.ot.AppliedArrayRemoveOperation
import com.convergencelabs.server.domain.model.data.DataValue
import com.convergencelabs.server.domain.model.ot.AppliedArrayReplaceOperation
import com.convergencelabs.server.domain.model.ot.AppliedArrayMoveOperation
import com.convergencelabs.server.domain.model.ot.AppliedArraySetOperation

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
  
  def dataValue(): ArrayValue = {
    ArrayValue(id, children map { _.dataValue() })
  }

  protected def child(childPath: Any): Try[Option[RealTimeValue]] = {
    childPath match {
      case index: Int =>
        Success(childValues.lift(index))
      case _ =>
        Failure(new IllegalArgumentException("Child path must be a Int for a RealTimeArray"))
    }
  }

  def processOperation(op: DiscreteOperation): Try[AppliedArrayOperation] = Try {
    op match {
      case insert: ArrayInsertOperation => processInsertOperation(insert)
      case remove: ArrayRemoveOperation => processRemoveOperation(remove)
      case replace: ArrayReplaceOperation => processReplaceOperation(replace)
      case reorder: ArrayMoveOperation => processReorderOperation(reorder)
      case value: ArraySetOperation => processSetValueOperation(value)
      case _ => throw new Error("Invalid operation for RealTimeObject")
    }
  }

  def processInsertOperation(op: ArrayInsertOperation): AppliedArrayInsertOperation = {
    val ArrayInsertOperation(id, noOp, index, value) = op
    val child = this.model.createValue(value, Some(this), Some(parentField))
    childValues = childValues.patch(index, List(child), 0)
    this.updateIndices(index + 1, childValues.length - 1)
    
    AppliedArrayInsertOperation(id, noOp, index, value)
  }

  def processRemoveOperation(op: ArrayRemoveOperation): AppliedArrayRemoveOperation = {
    val ArrayRemoveOperation(id, noOp, index) = op
    val oldChild = childValues(index)
    childValues = childValues.patch(index, List(), 1)
    this.updateIndices(index, childValues.length - 1)
    
    AppliedArrayRemoveOperation(id, noOp, index, Some(oldChild.dataValue()))
  }

  def processReplaceOperation(op: ArrayReplaceOperation): AppliedArrayReplaceOperation = {
    val ArrayReplaceOperation(id, noOp, index, value) = op
    val oldChild = childValues(index)
    val child = this.model.createValue(value, Some(this), Some(parentField))
    childValues = childValues.patch(index, List(child), 1)
    
    AppliedArrayReplaceOperation(id, noOp, index, value, Some(oldChild.dataValue()))
  }

  def processReorderOperation(op: ArrayMoveOperation): AppliedArrayMoveOperation = {
    val ArrayMoveOperation(id, noOp, fromIndex, toIndex) = op
    val child = childValues(fromIndex)
    childValues = childValues.patch(fromIndex, List(), 1)
    childValues = childValues.patch(toIndex, List(child), 0)
    this.updateIndices(fromIndex, toIndex)
    
    AppliedArrayMoveOperation(id, noOp, fromIndex, toIndex)
  }

  def processSetValueOperation(op: ArraySetOperation): AppliedArraySetOperation = {
    val ArraySetOperation(id, noOp, value) = op
    val oldValue = dataValue()
    var i = 0;
    childValues = value.map {
      v => this.model.createValue(v, Some(this), Some({ i += 1; i }))
    }
    AppliedArraySetOperation(id, noOp, value, Some(oldValue.children))
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
