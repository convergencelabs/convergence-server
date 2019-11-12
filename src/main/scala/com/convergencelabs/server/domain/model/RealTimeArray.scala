/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

package com.convergencelabs.server.domain.model

import scala.util.Failure
import scala.util.Success
import scala.util.Try

import com.convergencelabs.server.domain.model.data.ArrayValue
import com.convergencelabs.server.domain.model.ot.AppliedArrayInsertOperation
import com.convergencelabs.server.domain.model.ot.AppliedArrayMoveOperation
import com.convergencelabs.server.domain.model.ot.AppliedArrayOperation
import com.convergencelabs.server.domain.model.ot.AppliedArrayRemoveOperation
import com.convergencelabs.server.domain.model.ot.AppliedArrayReplaceOperation
import com.convergencelabs.server.domain.model.ot.AppliedArraySetOperation
import com.convergencelabs.server.domain.model.ot.ArrayInsertOperation
import com.convergencelabs.server.domain.model.ot.ArrayMoveOperation
import com.convergencelabs.server.domain.model.ot.ArrayRemoveOperation
import com.convergencelabs.server.domain.model.ot.ArrayReplaceOperation
import com.convergencelabs.server.domain.model.ot.ArraySetOperation
import com.convergencelabs.server.domain.model.ot.DiscreteOperation

class RealTimeArray(
  private[this] val value: ArrayValue,
  private[this] val parent: Option[RealTimeContainerValue],
  private[this] val parentField: Option[Any],
  private[this] val valueFactory: RealTimeValueFactory)
  extends RealTimeContainerValue(value.id, parent, parentField, List()) {

  var i = 0;
  private[this] var childValues = value.children.map {
    x => this.valueFactory.createValue(x, Some(this), Some({ i += 1; i }))
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

  def child(childPath: Any): Try[Option[RealTimeValue]] = {
    childPath match {
      case index: Int =>
        Success(childValues.lift(index))
      case _ =>
        Failure(new IllegalArgumentException("Child path must be an Int for a RealTimeArray"))
    }
  }

  protected def processValidatedOperation(op: DiscreteOperation): Try[AppliedArrayOperation] = {
    op match {
      case insert: ArrayInsertOperation =>
        processInsertOperation(insert)
      case remove: ArrayRemoveOperation =>
        processRemoveOperation(remove)
      case replace: ArrayReplaceOperation =>
        processReplaceOperation(replace)
      case reorder: ArrayMoveOperation =>
        processReorderOperation(reorder)
      case value: ArraySetOperation =>
        processSetValueOperation(value)
      case _ =>
        Failure(new IllegalArgumentException("Invalid operation type for RealTimeArray: " + op))
    }
  }

  private[this] def processInsertOperation(op: ArrayInsertOperation): Try[AppliedArrayInsertOperation] = {
    // FIXME: validate index
    val ArrayInsertOperation(id, noOp, index, value) = op
    val child = this.valueFactory.createValue(value, Some(this), Some(parentField))
    childValues = childValues.patch(index, List(child), 0)
    this.updateIndices(index + 1, childValues.length - 1)

    Success(AppliedArrayInsertOperation(id, noOp, index, value))
  }

  private[this] def processRemoveOperation(op: ArrayRemoveOperation): Try[AppliedArrayRemoveOperation] = {
    val ArrayRemoveOperation(id, noOp, index) = op
    if (index >= childValues.size) {
      Failure(new IllegalArgumentException("Index out of bounds"))
    } else {
      val oldChild = childValues(index)
      childValues = childValues.patch(index, List(), 1)
      this.updateIndices(index, childValues.length - 1)

      oldChild.detach()

      Success(AppliedArrayRemoveOperation(id, noOp, index, Some(oldChild.dataValue())))
    }
  }

  private[this] def processReplaceOperation(op: ArrayReplaceOperation): Try[AppliedArrayReplaceOperation] = {
    val ArrayReplaceOperation(id, noOp, index, value) = op
    if (index >= childValues.size) {
      Failure(new IllegalArgumentException("Index out of bounds"))
    } else {
      val oldChild = childValues(index)
      val child = this.valueFactory.createValue(value, Some(this), Some(parentField))
      childValues = childValues.patch(index, List(child), 1)

      oldChild.detach()

      Success(AppliedArrayReplaceOperation(id, noOp, index, value, Some(oldChild.dataValue())))
    }
  }

  private[this] def processReorderOperation(op: ArrayMoveOperation): Try[AppliedArrayMoveOperation] = {
    val ArrayMoveOperation(id, noOp, fromIndex, toIndex) = op
    // FIXME validate toIndex
    if (fromIndex >= childValues.size) {
      Failure(new IllegalArgumentException("fromIndex out of bounds"))
    } else {
      val child = childValues(fromIndex)
      childValues = childValues.patch(fromIndex, List(), 1)
      childValues = childValues.patch(toIndex, List(child), 0)
      this.updateIndices(fromIndex, toIndex)

      Success(AppliedArrayMoveOperation(id, noOp, fromIndex, toIndex))
    }
  }

  private[this] def processSetValueOperation(op: ArraySetOperation): Try[AppliedArraySetOperation] = {
    val ArraySetOperation(id, noOp, value) = op
    val oldValue = dataValue()

    this.detachChildren()

    var i = 0;
    childValues = value.map {
      v => this.valueFactory.createValue(v, Some(this), Some({ i += 1; i }))
    }
    Success(AppliedArraySetOperation(id, noOp, value, Some(oldValue.children)))
  }

  private[this] def updateIndices(fromIndex: Int, toIndex: Int): Unit = {
    for { i <- fromIndex to toIndex } {
      childValues(i).parentField = Some(i)
    }
  }

  override def detachChildren(): Unit = {
    childValues.foreach(_.detach())
  }
}
