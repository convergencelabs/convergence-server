package com.convergencelabs.server.domain.model.reference

import com.convergencelabs.server.domain.model.RealTimeValue
import com.convergencelabs.server.domain.model.SessionKey

abstract class ModelReference[T](
    val modelValue: RealTimeValue,
    val sessionId: String,
    val key: String) {

  protected var value: Option[T] = None

  def clear(): Unit = {
    this.value = None
  }

  def set(value: T): Unit = {
    this.value = Some(value)
  }

  def get(): Option[T] = {
    this.value
  }

  def isSet(): Boolean = {
    this.get().isDefined
  }

  def handleSet(): Unit = {
    clear()
  }
}
