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

import com.convergencelabs.server.domain.model.data.DoubleValue
import com.convergencelabs.server.domain.model.ot.AppliedNumberAddOperation
import com.convergencelabs.server.domain.model.ot.AppliedNumberOperation
import com.convergencelabs.server.domain.model.ot.AppliedNumberSetOperation
import com.convergencelabs.server.domain.model.ot.DiscreteOperation
import com.convergencelabs.server.domain.model.ot.NumberAddOperation
import com.convergencelabs.server.domain.model.ot.NumberSetOperation

class RealTimeDouble(
  private[this] val value: DoubleValue,
  private[this] val parent: Option[RealTimeContainerValue],
  private[this] val parentField: Option[Any])
  extends RealTimeValue(value.id, parent, parentField, List()) {

  var double: Double = value.value

  def data(): Double = {
    this.double
  }

  def dataValue(): DoubleValue = {
    DoubleValue(id, double)
  }

  protected def processValidatedOperation(op: DiscreteOperation): Try[AppliedNumberOperation] = {
    op match {
      case add: NumberAddOperation =>
        this.processAddOperation(add)
      case value: NumberSetOperation =>
        this.processSetOperation(value)
      case _ =>
        Failure(new IllegalArgumentException("Invalid operation type for RealTimeDouble: " + op))
    }
  }

  private[this] def processAddOperation(op: NumberAddOperation): Try[AppliedNumberAddOperation] = {
    val NumberAddOperation(id, noOp, value) = op
    double = double + value

    Success(AppliedNumberAddOperation(id, noOp, value))
  }

  private[this] def processSetOperation(op: NumberSetOperation): Try[AppliedNumberSetOperation] = {
    val NumberSetOperation(id, noOp, value) = op
    val oldValue = double
    double = value

    Success(AppliedNumberSetOperation(id, noOp, value, Some(oldValue)))
  }
}
