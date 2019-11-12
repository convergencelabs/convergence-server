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

import com.convergencelabs.server.domain.model.data.StringValue
import com.convergencelabs.server.domain.model.ot.AppliedStringInsertOperation
import com.convergencelabs.server.domain.model.ot.AppliedStringOperation
import com.convergencelabs.server.domain.model.ot.AppliedStringRemoveOperation
import com.convergencelabs.server.domain.model.ot.AppliedStringSetOperation
import com.convergencelabs.server.domain.model.ot.DiscreteOperation
import com.convergencelabs.server.domain.model.ot.StringInsertOperation
import com.convergencelabs.server.domain.model.ot.StringRemoveOperation
import com.convergencelabs.server.domain.model.ot.StringSetOperation
import com.convergencelabs.server.domain.model.reference.PositionalInsertAware
import com.convergencelabs.server.domain.model.reference.PositionalRemoveAware

class RealTimeString(
  private[this] val value: StringValue,
  private[this] val parent: Option[RealTimeContainerValue],
  private[this] val parentField: Option[Any])
  extends RealTimeValue(
    value.id,
    parent,
    parentField,
    List(ReferenceType.Index, ReferenceType.Range)) {

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

      this.referenceManager.referenceMap.getAll().foreach {
        case x: PositionalInsertAware => x.handlePositionalInsert(index, value.length)
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

      this.referenceManager.referenceMap.getAll().foreach {
        case x: PositionalRemoveAware => x.handlePositionalRemove(index, value.length)
      }

      Success(AppliedStringRemoveOperation(id, noOp, index, value.length(), Some(value)))
    }
  }

  private[this] def processSetOperation(op: StringSetOperation): Try[AppliedStringSetOperation] = {
    val StringSetOperation(id, noOp, value) = op

    val oldValue = string
    this.string = value
    this.referenceManager.referenceMap.getAll().foreach { x => x.handleSet() }

    Success(AppliedStringSetOperation(id, noOp, value, Some(oldValue)))
  }
}
