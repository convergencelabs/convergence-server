package com.convergencelabs.server.domain.model.reference

import com.convergencelabs.server.domain.model.ot.xform.IndexTransformer

class RangeReference(
  modelValue: Any,
  sessionId: String,
  key: String)
    extends ModelReference[(Int, Int)](modelValue, sessionId, key)
    with PositionalInsertAware
    with PositionalRemoveAware
    with PositionalReorderAware {

  def handlePositionalInsert(index: Int, length: Int): Unit = {
    this.values = this.values.map { v =>
      val xFormed = IndexTransformer.handleInsert(List(v._1, v._2), index, length)
      (xFormed(0), xFormed(1))
    }
  }

  def handlePositionalRemove(index: Int, length: Int): Unit = {
    this.values = this.values.map { v =>
      val xFormed = IndexTransformer.handleRemove(List(v._1, v._2), index, length)
      (xFormed(0), xFormed(1))
    }
  }

  def handlePositionalReorder(fromIndex: Int, toIndex: Int): Unit = {
    this.values = this.values.map { v =>
      val xFormed = IndexTransformer.handleReorder(List(v._1, v._2), fromIndex, toIndex)
      (xFormed(0), xFormed(1))
    }
  }
}
