package com.convergencelabs.server.domain.model

import org.json4s.JsonAST.JBool
import com.convergencelabs.server.domain.model.ot.BooleanSetOperation
import com.convergencelabs.server.domain.model.ot.Operation
import scala.util.Try
import com.convergencelabs.server.domain.model.ot.DiscreteOperation

class RealTimeBoolean(
  private[this] val model: RealTimeModel,
  private[this] val parent: Option[RealTimeContainerValue],
  private[this] val parentField: Option[Any],
  private[this] var boolVal: JBool)
    extends RealTimeValue(model, parent, parentField) {

  private[this] var boolean = boolVal.values
  
  def data(): Boolean = {
    this.boolean
  }

  def processOperation(op: DiscreteOperation): Try[Unit] = Try {
    op match {
      case value: BooleanSetOperation => this.processSetOperation(value)
      case _ => throw new IllegalArgumentException("Invalid operation type in RealTimeBoolean");
    }
  }

  private[this] def processSetOperation(op: BooleanSetOperation): Unit = {
    this.boolean = op.value
  }
}