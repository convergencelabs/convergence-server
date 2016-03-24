package com.convergencelabs.server.domain.model.reference

import com.convergencelabs.server.domain.model.SessionKey
import com.convergencelabs.server.domain.model.RealTimeValue
import com.convergencelabs.server.domain.model.ot.xform.IndexTransformer

class IndexReference(
  source: RealTimeValue,
  sessionId: String,
  key: String)
    extends ModelReference[Int](source, sessionId, key)
    with PositionalInsertAware
    with PositionalRemoveAware
    with PositionalReorderAware {

  def handlePositionalInsert(index: Int, length: Int): Unit = {
    this.value = this.value.map { v => IndexTransformer.handleInsert(List(v), index, length)(0) }
  }

  def handlePositionalRemove(index: Int, length: Int): Unit = {
    this.value = this.value.map { v => IndexTransformer.handleRemove(List(v), index, length)(0) }
  }

  def handlePositionalReorder(fromIndex: Int, toIndex: Int): Unit = {
    this.value = this.value.map { v => IndexTransformer.handleReorder(List(v), fromIndex, toIndex)(0) }
  }
}
