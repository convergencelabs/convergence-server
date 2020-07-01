package com.convergencelabs.convergence.server.backend.services.domain.model.ot

class MockStringModel(private var state: String) extends MockModel {

  def updateModel(op: DiscreteOperation): Unit = {
    op match {
      case insert: StringInsertOperation => handleInsert(insert)
      case remove: StringRemoveOperation => handleRemove(remove)
      case set: StringSetOperation => handleSet(set)
      case x: Any => throw new IllegalArgumentException()
    }
  }

  private def handleInsert(op: StringInsertOperation): Unit = {
    state = state.patch(op.index, op.value, 0)
  }

  private def handleRemove(op: StringRemoveOperation): Unit = {
    state = state.patch(op.index, Nil, op.value.length)
  }

  private def handleSet(op: StringSetOperation): Unit = {
    state = op.value
  }

  def getData(): String = {
    state
  }
}
