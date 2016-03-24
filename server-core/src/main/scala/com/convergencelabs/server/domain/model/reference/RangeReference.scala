package com.convergencelabs.server.domain.model.reference

import com.convergencelabs.server.domain.model.SessionKey
import com.convergencelabs.server.domain.model.RealTimeValue
import com.convergencelabs.server.domain.model.ot.xform.IndexTransformer

class RangeReference(
  modelValue: RealTimeValue,
  sessionId: String,
  key: String)
    extends ModelReference[(Int, Int)](modelValue, sessionId, key) {
  
  def handleInsert(index: Int, length: Int): Unit = {
    this.value = this.value.map { v =>
      val xFormed = IndexTransformer.handleInsert(List(v._1, v._2), index, length)
      (xFormed(0), xFormed(1))
    }
  }

  def handleRemove(index: Int, length: Int): Unit = {
    this.value = this.value.map { v =>
      val xFormed = IndexTransformer.handleRemove(List(v._1, v._2), index, length)
      (xFormed(0), xFormed(1))
    }
  }

  def handleReorder(fromIndex: Int, toIndex: Int): Unit = {
    this.value = this.value.map { v =>
      val xFormed = IndexTransformer.handleReorder(List(v._1, v._2), fromIndex, toIndex)
      (xFormed(0), xFormed(1))
    }
  }
}
