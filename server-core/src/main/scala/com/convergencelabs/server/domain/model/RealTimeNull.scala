package com.convergencelabs.server.domain.model

import scala.util.Try
import com.convergencelabs.server.domain.model.ot.DiscreteOperation
import com.convergencelabs.server.domain.model.data.NullValue

class RealTimeNull(
  private[this] val value: NullValue,
  private[this] val model: RealTimeModel,
  private[this] val parent: Option[RealTimeContainerValue],
  private[this] val parentField: Option[Any])
    extends RealTimeValue(value.id, model, parent, parentField) {

  def data(): Null = {
    null
  }

  def processOperation(op: DiscreteOperation): Try[Unit] = Try {
    throw new IllegalArgumentException("Invalid operation type in RealTimeDouble");
  }
}
