package com.convergencelabs.server.domain.model

import com.convergencelabs.server.domain.model.ot.Operation
import scala.util.Try
import com.convergencelabs.server.domain.model.ot.DiscreteOperation
import scala.util.Failure

abstract class RealTimeValue(
    private[model] val id: String,
    private[model] val model: RealTimeModel,
    private[model] var parent: Option[RealTimeContainerValue],
    private[model] var parentField: Option[Any]) {
  
  model.registerValue(this)
  
  def path(): List[Any] = {
    parent match {
      case None => List()
      case Some(p) => p.path() :+ parentField
    }
  }
  
  def detach(): Unit = {
    model.unregisterValue(this)
  }
  
  def data(): Any
  
  def processOperation(op: DiscreteOperation, path: List[Any]): Try[Unit]=  {
    path match {
      case Nil =>
        processOperation(op)
      case path =>
        Failure(new IllegalArgumentException("Value type does not have children"))
    }
  }
  
  def processOperation(operation: DiscreteOperation): Try[Unit]
}
