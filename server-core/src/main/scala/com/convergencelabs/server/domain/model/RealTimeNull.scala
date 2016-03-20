package com.convergencelabs.server.domain.model

import scala.util.Try
import com.convergencelabs.server.domain.model.ot.DiscreteOperation

class RealTimeNull(
  private[this] val model: RealTimeModel,
  private[this] val parent: Option[RealTimeContainerValue],
  private[this] val parentField: Any)
    extends RealTimeValue(model, parent, parentField) {

  def data(): Null = {
    null
  }

  def processOperation(op: DiscreteOperation): Try[Unit] = Try {
    throw new IllegalArgumentException("Invalid operation type in RealTimeDouble");
  }
}
