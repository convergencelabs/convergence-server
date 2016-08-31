package com.convergencelabs.server.domain.model

import scala.util.Try
import com.convergencelabs.server.domain.model.ot.DiscreteOperation
import com.convergencelabs.server.domain.model.data.NullValue
import com.convergencelabs.server.domain.model.ot.AppliedDiscreteOperation

class RealTimeNull(
  private[this] val value: NullValue,
  private[this] val model: RealTimeModel,
  private[this] val parent: Option[RealTimeContainerValue],
  private[this] val parentField: Option[Any])
    extends RealTimeValue(value.id, model, parent, parentField, List()) {

  def data(): Null = {
    null  // scalastyle:ignore null
  }
  
  def dataValue(): NullValue = {
    value
  }

  def processOperation(op: DiscreteOperation): Try[AppliedDiscreteOperation] = Try {
    throw new IllegalArgumentException("Invalid operation type in RealTimeDouble");
  }
}
