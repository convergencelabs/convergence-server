package com.convergencelabs.server.domain.model

import org.json4s.JsonAST.JBool
import com.convergencelabs.server.domain.model.ot.BooleanSetOperation
import com.convergencelabs.server.domain.model.ot.Operation
import scala.util.Try
import com.convergencelabs.server.domain.model.ot.DiscreteOperation
import com.convergencelabs.server.domain.model.data.BooleanValue
import scala.util.Failure

class RealTimeBoolean(
  private[this] val value: BooleanValue,
  private[this] val model: RealTimeModel,
  private[this] val parent: Option[RealTimeContainerValue],
  private[this] val parentField: Option[Any])
    extends RealTimeValue(value.id, model, parent, parentField) {

  private[this] var boolean = value.value

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
  
  def processReferenceEvent(event: ModelReferenceEvent): Try[Unit] = {
    Failure(throw new IllegalArgumentException("RealTimeNull does not accept references"))
  }
}