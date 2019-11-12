package com.convergencelabs.convergence.server.domain.model.ot

trait MockModel {
  def processOperation(op: DiscreteOperation): Unit = {
    op.noOp match {
      case true =>
      case false => updateModel(op)
    }
  }

  protected def updateModel(op: DiscreteOperation): Unit

  def getData(): Any
}
