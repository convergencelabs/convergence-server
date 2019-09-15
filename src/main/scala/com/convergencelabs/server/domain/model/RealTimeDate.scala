package com.convergencelabs.server.domain.model

import java.time.Instant

import scala.util.Failure
import scala.util.Success
import scala.util.Try

import com.convergencelabs.server.domain.model.data.DateValue
import com.convergencelabs.server.domain.model.ot.AppliedDateOperation
import com.convergencelabs.server.domain.model.ot.AppliedDateSetOperation
import com.convergencelabs.server.domain.model.ot.DateSetOperation
import com.convergencelabs.server.domain.model.ot.DiscreteOperation

class RealTimeDate(
  private[this] val value: DateValue,
  private[this] val parent: Option[RealTimeContainerValue],
  private[this] val parentField: Option[Any])
  extends RealTimeValue(value.id, parent, parentField, List()) {

  private[this] var date = value.value

  def data(): Instant = {
    date
  }

  def dataValue(): DateValue = {
    DateValue(id, date)
  }

  protected def processValidatedOperation(op: DiscreteOperation): Try[AppliedDateOperation] = {
    op match {
      case value: DateSetOperation =>
        this.processSetOperation(value)
      case _ =>
        Failure(new IllegalArgumentException("Invalid operation type fore RealTimeDate: " + op))
    }
  }

  private[this] def processSetOperation(op: DateSetOperation): Try[AppliedDateSetOperation] = {
    val DateSetOperation(id, noOp, value) = op

    val oldValue = data()
    date = value

    Success(AppliedDateSetOperation(id, noOp, value, Some(oldValue)))
  }
}
