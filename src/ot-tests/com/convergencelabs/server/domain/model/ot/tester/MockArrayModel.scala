package com.convergencelabs.convergence.server.backend.services.domain.model.ot

import scala.collection.JavaConverters.asScalaBufferConverter

class MockArrayModel(private var state: List[Any]) extends MockModel {

  def updateModel(op: DiscreteOperation): Unit = {
    op match {
      case insert: ArrayInsertOperation => handleInsert(insert)
      case remove: ArrayRemoveOperation => handleRemove(remove)
      case replace: ArrayReplaceOperation => handleReplace(replace)
      case move: ArrayMoveOperation => handleMove(move)
      case set: ArraySetOperation => handleSet(set)
      case x: Any =>
        throw new IllegalArgumentException()
    }
  }

  private def handleInsert(op: ArrayInsertOperation): Unit = {
    state = state.patch(op.index, Seq(op.value), 0)
  }

  private def handleRemove(op: ArrayRemoveOperation): Unit = {
    state = state.patch(op.index, Nil, 1)
  }

  private def handleReplace(op: ArrayReplaceOperation): Unit = {
    state = state.patch(op.index, Seq(op.value), 1)
  }

  private def handleMove(op: ArrayMoveOperation): Unit = {
    val element = state(op.fromIndex)
    state = state.patch(op.fromIndex, Nil, 1)
    state = state.patch(op.toIndex, Seq(element), 0)
  }

  private def handleSet(op: ArraySetOperation): Unit = {
    state = op.value
  }

  def getData(): List[Any] = {
    state
  }
}
