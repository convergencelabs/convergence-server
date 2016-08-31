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
import com.convergencelabs.server.domain.model.ot.AppliedStringOperation
import com.convergencelabs.server.domain.model.ot.AppliedStringInsertOperation
import com.convergencelabs.server.domain.model.ot.AppliedStringRemoveOperation
import com.convergencelabs.server.domain.model.ot.AppliedStringSetOperation

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
  
  def dataValue(): StringValue = {
    StringValue(id, string)
  }

  def processOperation(op: DiscreteOperation): Try[AppliedStringOperation] = Try {
    op match {
      case insert: StringInsertOperation => this.processInsertOperation(insert)
      case remove: StringRemoveOperation => this.processRemoveOperation(remove)
      case value: StringSetOperation => this.processSetOperation(value)
      case _ => throw new IllegalArgumentException("Invalid operation type in RealTimeString")
    }
  }

  private[this] def processInsertOperation(op: StringInsertOperation): AppliedStringInsertOperation = {
    val StringInsertOperation(id, noOp, index, value) = op
    
    if (this.string.length < index || index < 0) {
      throw new IllegalArgumentException("Index out of bounds: " + index)
    }
    this.string = this.string.slice(0, index) + value + this.string.slice(index, this.string.length)

    this.referenceManager.referenceMap.getAll().foreach {
      case x: PositionalInsertAware => x.handlePositionalInsert(index, value.length)
    }
    
    AppliedStringInsertOperation(id, noOp, index, value)
  }

  private[this] def processRemoveOperation(op: StringRemoveOperation): AppliedStringRemoveOperation = {
    val StringRemoveOperation(id, noOp, index, value) = op
    
    if (this.string.length < index + value.length || index < 0) {
      throw new Error("Index out of bounds!")
    }
    
    val oldValue = this.string.slice(index, value.length) 
    
    this.string = this.string.slice(0, index) + this.string.slice(index + value.length, this.string.length)

    this.referenceManager.referenceMap.getAll().foreach {
      case x: PositionalRemoveAware => x.handlePositionalRemove(index, value.length)
    }
    
    AppliedStringRemoveOperation(id, noOp, index, value.length(), Some(oldValue))
  }

  private[this] def processSetOperation(op: StringSetOperation): AppliedStringSetOperation = {
    val StringSetOperation(id, noOp, value) = op
    
    val oldValue = string
    this.string = value
    this.referenceManager.referenceMap.getAll().foreach { x => x.handleSet() }
    
    AppliedStringSetOperation(id, noOp, value, Some(oldValue))
  }
}
