package com.convergencelabs.server.domain.model

import org.json4s.JsonAST.JBool
import com.convergencelabs.server.domain.model.ot.Operation
import scala.util.Try
import com.convergencelabs.server.domain.model.ot.DiscreteOperation
import scala.util.Failure
import com.convergencelabs.server.domain.model.data.DateValue
import java.time.Instant
import com.convergencelabs.server.domain.model.ot.AppliedDateOperation
import com.convergencelabs.server.domain.model.ot.DateSetOperation
import com.convergencelabs.server.domain.model.ot.AppliedDateSetOperation

class RealTimeDate(
  private[this] val value: DateValue,
  private[this] val model: RealTimeModel,
  private[this] val parent: Option[RealTimeContainerValue],
  private[this] val parentField: Option[Any])
    extends RealTimeValue(value.id, model, parent, parentField, List()) {

  private[this] var date = value.value

  def data(): Instant = {
    date
  }
  
  def dataValue(): DateValue = {
    DateValue(id, date)
  }

  def processOperation(op: DiscreteOperation): Try[AppliedDateOperation] = Try {
    op match {
      case value: DateSetOperation => this.processSetOperation(value)
      case _ => throw new IllegalArgumentException("Invalid operation type in RealTimeBoolean");
    }
  }

  private[this] def processSetOperation(op: DateSetOperation): AppliedDateSetOperation = {
    val DateSetOperation(id, noOp, value) = op
    
    val oldValue = data()
    date = value
    
    AppliedDateSetOperation(id, noOp, value, Some(oldValue))
  }
}
