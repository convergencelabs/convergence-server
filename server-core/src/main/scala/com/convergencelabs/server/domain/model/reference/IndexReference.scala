package com.convergencelabs.server.domain.model.reference

import com.convergencelabs.server.domain.model.SessionKey
import com.convergencelabs.server.domain.model.RealTimeValue
import com.convergencelabs.server.domain.model.ot.xform.IndexTransformer

class IndexReference(
  source: RealTimeValue,
  sessionId: String,
  key: String)
    extends ModelReference[Int](source, sessionId, key) {
  
  def handleInsert(index: Int, length: Int): Unit = {
    this.value = this.value.map { v => IndexTransformer.handleInsert(List(v), index, length)(0) }
  }

  def handleRemove(index: Int, length: Int): Unit = {
    this.value = this.value.map { v => IndexTransformer.handleRemove(List(v), index, length)(0) }
  }

  def handleReorder(fromIndex: Int, toIndex: Int): Unit = {
    this.value = this.value.map { v => IndexTransformer.handleReorder(List(v), fromIndex, toIndex)(0) }
  }
}
