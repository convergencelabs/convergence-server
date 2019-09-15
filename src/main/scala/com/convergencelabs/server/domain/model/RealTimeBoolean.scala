package com.convergencelabs.server.domain.model

import scala.util.Failure
import scala.util.Success
import scala.util.Try

import com.convergencelabs.server.domain.model.data.BooleanValue
import com.convergencelabs.server.domain.model.ot.AppliedBooleanOperation
import com.convergencelabs.server.domain.model.ot.AppliedBooleanSetOperation
import com.convergencelabs.server.domain.model.ot.BooleanSetOperation
import com.convergencelabs.server.domain.model.ot.DiscreteOperation

class RealTimeBoolean(
  private[this] val value: BooleanValue,
  private[this] val parent: Option[RealTimeContainerValue],
  private[this] val parentField: Option[Any])
  extends RealTimeValue(value.id, parent, parentField, List()) {

  private[this] var boolean = value.value

  def data(): Boolean = {
    boolean
  }

  def dataValue(): BooleanValue = {
    BooleanValue(id, boolean)
  }

  protected def processValidatedOperation(op: DiscreteOperation): Try[AppliedBooleanOperation] = {
    op match {
      case value: BooleanSetOperation =>
        this.processSetOperation(value)
      case _ =>
        Failure(new IllegalArgumentException("Invalid operation type for RealTimeBoolean: " + op))
    }
  }

  private[this] def processSetOperation(op: BooleanSetOperation): Try[AppliedBooleanSetOperation] = {
    val BooleanSetOperation(id, noOp, value) = op

    val oldValue = data()
    boolean = value

    Success(AppliedBooleanSetOperation(id, noOp, value, Some(oldValue)))
  }
}
