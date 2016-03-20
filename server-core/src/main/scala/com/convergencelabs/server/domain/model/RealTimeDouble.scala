package com.convergencelabs.server.domain.model

import com.convergencelabs.server.domain.model.ot.DiscreteOperation
import com.convergencelabs.server.domain.model.ot.NumberAddOperation
import com.convergencelabs.server.domain.model.ot.NumberSetOperation
import org.json4s.JsonAST.JDouble
import scala.util.Try

class RealTimeDouble(
  private[this] val model: RealTimeModel,
  private[this] val parent: Option[RealTimeContainerValue],
  private[this] val parentField: Option[Any],
  private[this] var dblVal: JDouble)
    extends RealTimeValue(model, parent, parentField) {

  var double: Double = dblVal.values
  
  def value(): Double = {
    this.double
  }

  def processOperation(op: DiscreteOperation): Try[Unit] = Try {
    op match {
      case add: NumberAddOperation => this.processAddOperation(add)
      case value: NumberSetOperation => this.processSetOperation(value)
      case _ => throw new IllegalArgumentException("Invalid operation type in RealTimeDouble");
    }
  }

  private[this] def processAddOperation(op: NumberAddOperation): Unit = {
    this.double = this.double + op.value.values
  }

  private[this] def processSetOperation(op: NumberSetOperation): Unit = {
    this.double = op.value.values
  }
}
