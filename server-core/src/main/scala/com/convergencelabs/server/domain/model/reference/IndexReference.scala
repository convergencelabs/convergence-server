package com.convergencelabs.server.domain.model.reference

import com.convergencelabs.server.domain.model.ot.xform.IndexTransformer

class IndexReference(
  source: Any,
  sessionId: String,
  key: String)
    extends ModelReference[Int](source, sessionId, key)
    with PositionalInsertAware
    with PositionalRemoveAware
    with PositionalReorderAware {

  def handlePositionalInsert(index: Int, length: Int): Unit = {
    this.values = IndexTransformer.handleInsert(this.values, index, length)
  }

  def handlePositionalRemove(index: Int, length: Int): Unit = {
    this.values = IndexTransformer.handleRemove(this.values, index, length)
  }

  def handlePositionalReorder(fromIndex: Int, toIndex: Int): Unit = {
    this.values = IndexTransformer.handleReorder(this.values, fromIndex, toIndex)
  }
}
