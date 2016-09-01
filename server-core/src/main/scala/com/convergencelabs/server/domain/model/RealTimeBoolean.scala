package com.convergencelabs.server.domain.model

import org.json4s.JsonAST.JBool
import com.convergencelabs.server.domain.model.ot.BooleanSetOperation
import com.convergencelabs.server.domain.model.ot.Operation
import scala.util.Try
import com.convergencelabs.server.domain.model.ot.DiscreteOperation
import com.convergencelabs.server.domain.model.data.BooleanValue
import scala.util.Failure
import com.convergencelabs.server.domain.model.ot.AppliedBooleanOperation
import com.convergencelabs.server.domain.model.ot.AppliedBooleanSetOperation

class RealTimeBoolean(
  private[this] val value: BooleanValue,
  private[this] val model: RealTimeModel,
  private[this] val parent: Option[RealTimeContainerValue],
  private[this] val parentField: Option[Any])
    extends RealTimeValue(value.id, model, parent, parentField, List()) {

  private[this] var boolean = value.value

  def data(): Boolean = {
    boolean
  }
  
  def dataValue(): BooleanValue = {
    BooleanValue(id, boolean)
  }

  def processOperation(op: DiscreteOperation): Try[AppliedBooleanOperation] = Try {
    op match {
      case value: BooleanSetOperation => this.processSetOperation(value)
      case _ => throw new IllegalArgumentException("Invalid operation type in RealTimeBoolean");
    }
  }

  private[this] def processSetOperation(op: BooleanSetOperation): AppliedBooleanSetOperation = {
    val BooleanSetOperation(id, noOp, value) = op
    
    val oldValue = data()
    boolean = value
    
    AppliedBooleanSetOperation(id, noOp, value, Some(oldValue))
  }
}
