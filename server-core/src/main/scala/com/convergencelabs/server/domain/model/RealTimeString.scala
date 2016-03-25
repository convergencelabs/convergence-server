package com.convergencelabs.server.domain.model

import com.convergencelabs.server.domain.model.ot.DiscreteOperation
import com.convergencelabs.server.domain.model.ot.StringInsertOperation
import com.convergencelabs.server.domain.model.ot.StringSetOperation
import com.convergencelabs.server.domain.model.ot.StringRemoveOperation
import org.json4s.JsonAST.JString
import scala.util.Try
import com.convergencelabs.server.domain.model.data.StringValue
import scala.util.Success
import com.convergencelabs.server.domain.model.reference.ReferenceManager
import com.convergencelabs.server.domain.model.reference.PositionalRemoveAware
import com.convergencelabs.server.domain.model.reference.PositionalInsertAware

class RealTimeString(
  private[this] val value: StringValue,
  private[this] val model: RealTimeModel,
  private[this] val parent: Option[RealTimeContainerValue],
  private[this] val parentField: Option[Any])
    extends RealTimeValue(
      value.id,
      model,
      parent,
      parentField,
      List(ReferenceType.Index, ReferenceType.Range)) {

  private[this] var string = value.value
  def data(): String = {
    this.string
  }

  def processOperation(op: DiscreteOperation): Try[Unit] = Try {
    op match {
      case insert: StringInsertOperation => this.processInsertOperation(insert)
      case remove: StringRemoveOperation => this.processRemoveOperation(remove)
      case value: StringSetOperation => this.processSetOperation(value)
      case _ => throw new IllegalArgumentException("Invalid operation type in RealTimeString")
    }
  }

  private[this] def processInsertOperation(op: StringInsertOperation): Unit = {
    if (this.string.length < op.index || op.index < 0) {
      throw new IllegalArgumentException("Index out of bounds: " + op.index)
    }
    this.string = this.string.slice(0, op.index) + op.value + this.string.slice(op.index, this.string.length)

    this.referenceManager.referenceMap.getAll().foreach {
      case x: PositionalInsertAware => x.handlePositionalInsert(op.index, op.value.length)
    }
  }

  private[this] def processRemoveOperation(op: StringRemoveOperation): Unit = {
    if (this.string.length < op.index + op.value.length || op.index < 0) {
      throw new Error("Index out of bounds!")
    }
    this.string = this.string.slice(0, op.index) + this.string.slice(op.index + op.value.length, this.string.length)

    this.referenceManager.referenceMap.getAll().foreach {
      case x: PositionalRemoveAware => x.handlePositionalRemove(op.index, op.value.length)
    }
  }

  private[this] def processSetOperation(op: StringSetOperation): Unit = {
    this.string = op.value
    this.referenceManager.referenceMap.getAll().foreach { x => x.handleSet() }
  }
}