package com.convergencelabs.server.domain.model.reference

abstract class ModelReference[T](
    val modelValue: Any,
    val sessionId: String,
    val key: String) {

  protected var values: List[T] = List()

  def clear(): Unit = {
    this.values = List()
  }

  def set(values: List[T]): Unit = {
    this.values = values
  }

  def get(): List[T] = {
    this.values
  }

  def isSet(): Boolean = {
    !this.get().isEmpty
  }

  def handleSet(): Unit = {
    clear()
  }
}
