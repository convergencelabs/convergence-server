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

package com.convergencelabs.convergence.server.domain.model

import com.convergencelabs.convergence.server.domain.model.data.StringValue
import com.convergencelabs.convergence.server.domain.model.ot._
import com.convergencelabs.convergence.server.domain.model.reference.{PositionalInsertAware, PositionalRemoveAware}

import scala.util.{Failure, Success, Try}

class RealTimeString(private[this] val value: StringValue,
                     private[this] val parent: Option[RealTimeContainerValue],
                     private[this] val parentField: Option[Any])
  extends RealTimeValue(
    value.id,
    parent,
    parentField,
    List(classOf[IndexReferenceValues], classOf[RangeReferenceValues])) {

  private[this] var string = value.value

  def data(): String = {
    this.string
  }

  def dataValue(): StringValue = {
    StringValue(id, string)
  }

  protected def processValidatedOperation(op: DiscreteOperation): Try[AppliedStringOperation] = {
    op match {
      case insert: StringInsertOperation =>
        this.processInsertOperation(insert)
      case remove: StringRemoveOperation =>
        this.processRemoveOperation(remove)
      case value: StringSetOperation =>
        this.processSetOperation(value)
      case op =>
        Failure(new IllegalArgumentException("Invalid operation type for RealTimeString: " + op))
    }
  }

  private[this] def processInsertOperation(op: StringInsertOperation): Try[AppliedStringInsertOperation] = {
    val StringInsertOperation(id, noOp, index, value) = op

    if (this.string.length < index || index < 0) {
      Failure(new IllegalArgumentException("Index out of bounds: " + index))
    } else {
      this.string = this.string.slice(0, index) + value + this.string.slice(index, this.string.length)

      this.referenceManager.referenceMap().getAll.foreach {
        case x: PositionalInsertAware => x.handlePositionalInsert(index, value.length)
        case _ => // no-op
      }

      Success(AppliedStringInsertOperation(id, noOp, index, value))
    }
  }

  private[this] def processRemoveOperation(op: StringRemoveOperation): Try[AppliedStringRemoveOperation] = {
    val StringRemoveOperation(id, noOp, index, value) = op

    if (this.string.length < index + value.length || index < 0) {
      Failure(new Error("Index out of bounds!"))
    } else {
      this.string = this.string.slice(0, index) + this.string.slice(index + value.length, this.string.length)

      this.referenceManager.referenceMap().getAll.foreach {
        case x: PositionalRemoveAware => x.handlePositionalRemove(index, value.length)
        case _ => // no-op
      }

      Success(AppliedStringRemoveOperation(id, noOp, index, value.length(), Some(value)))
    }
  }

  private[this] def processSetOperation(op: StringSetOperation): Try[AppliedStringSetOperation] = {
    val StringSetOperation(id, noOp, value) = op

    val oldValue = string
    this.string = value
    this.referenceManager.referenceMap().getAll.foreach { x => x.handleModelValueSet() }

    Success(AppliedStringSetOperation(id, noOp, value, Some(oldValue)))
  }
}
