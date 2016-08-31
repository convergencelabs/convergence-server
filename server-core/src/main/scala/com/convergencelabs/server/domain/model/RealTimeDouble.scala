package com.convergencelabs.server.domain.model

import com.convergencelabs.server.domain.model.ot.DiscreteOperation
import com.convergencelabs.server.domain.model.ot.NumberAddOperation
import com.convergencelabs.server.domain.model.ot.NumberSetOperation
import org.json4s.JsonAST.JDouble
import scala.util.Try
import com.convergencelabs.server.domain.model.data.DoubleValue
import com.convergencelabs.server.domain.model.ot.AppliedNumberOperation
import com.convergencelabs.server.domain.model.ot.AppliedNumberSetOperation
import com.convergencelabs.server.domain.model.ot.AppliedNumberAddOperation

class RealTimeDouble(
  private[this] val value: DoubleValue,
  private[this] val model: RealTimeModel,
  private[this] val parent: Option[RealTimeContainerValue],
  private[this] val parentField: Option[Any])
    extends RealTimeValue(value.id, model, parent, parentField, List()) {

  var double: Double = value.value

  def data(): Double = {
    this.double
  }
  
  def dataValue(): DoubleValue = {
    DoubleValue(id, double)
  }

  def processOperation(op: DiscreteOperation): Try[AppliedNumberOperation] = Try {
    op match {
      case add: NumberAddOperation => this.processAddOperation(add)
      case value: NumberSetOperation => this.processSetOperation(value)
      case _ => throw new IllegalArgumentException("Invalid operation type in RealTimeDouble");
    }
  }

  private[this] def processAddOperation(op: NumberAddOperation): AppliedNumberAddOperation = {
    val NumberAddOperation(id, noOp, value) = op
    double = double + value
    
    AppliedNumberAddOperation(id, noOp, value)
  }

  private[this] def processSetOperation(op: NumberSetOperation): AppliedNumberSetOperation = {
    val NumberSetOperation(id, noOp, value) = op
    val oldValue = double
    double = value
    
    AppliedNumberSetOperation(id, noOp, value, Some(oldValue))
  }
}
