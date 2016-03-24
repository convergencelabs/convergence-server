package com.convergencelabs.server.domain.model.reference

import com.convergencelabs.server.domain.model.SessionKey
import com.convergencelabs.server.domain.model.RealTimeValue
import com.convergencelabs.server.domain.model.ot.xform.IndexTransformer

class IndexReference(
  modelValue: RealTimeValue,
  key: String,
  sessionKey: String,
  var value: Int)
    extends ModelReference(modelValue, key, sessionKey) {

  def handleInsert(index: Int, length: Int): Unit = {
    value = IndexTransformer.handleInsert(List(this.value), index, length)(0)
  }

  def handleRemove(index: Int, length: Int): Unit = {
    value = IndexTransformer.handleRemove(List(this.value), index, length)(0)
  }

  def handleReorder(fromIndex: Int, toIndex: Int): Unit = {
    value = IndexTransformer.handleReorder(List(this.value), fromIndex, toIndex)(0)
  }
}
