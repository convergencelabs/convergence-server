package com.convergencelabs.server.domain.model.ot.ops

import com.fasterxml.jackson.databind.JsonNode
import com.convergencelabs.server.domain.model.ot.xform.ObjectOperationTransformer

sealed trait ObjectOperation {
  def transform(other: ObjectOperation): (DiscreteOperation, DiscreteOperation)
}

//TODO: Handle Undefined Conecept
case class ObjectSetPropertyOperation(override val path: List[Any], override val noOp: Boolean, property: String, oldValue: JsonNode, newValue: JsonNode) extends DiscreteOperation(path, noOp) with ObjectOperation {

  def invert(): DiscreteOperation = ObjectSetPropertyOperation(path, noOp, property, newValue, oldValue)

  def copyBuilder(): ObjectSetPropertyOperation.Builder = new ObjectSetPropertyOperation.Builder(path, noOp, property, oldValue, newValue)

  def transform(other: ObjectOperation): (DiscreteOperation, DiscreteOperation) = other match {
    case other: ObjectSetPropertyOperation => ObjectOperationTransformer.transformSetPropertySetProperty(this, other)
    case other: ObjectRemovePropertyOperation => ObjectOperationTransformer.transformSetPropertyRemoveProperty(this, other)
    case other: ObjectSetOperation => ObjectOperationTransformer.transformSetPropertySet(this, other)
  }
}

object ObjectSetPropertyOperation {
  class Builder(path: List[Any], noOp: scala.Boolean, var property: String, var oldValue: JsonNode, var newValue: JsonNode) extends DiscreteOperation.Builder(path, noOp) {
    def build(): ObjectSetPropertyOperation = ObjectSetPropertyOperation(path, noOp, property, oldValue, newValue)
  }
}

case class ObjectRemovePropertyOperation(override val path: List[Any], override val noOp: Boolean, property: String, oldValue: JsonNode) extends DiscreteOperation(path, noOp) with ObjectOperation {

  def invert(): DiscreteOperation = ObjectSetPropertyOperation(path, noOp, property, null, oldValue)

  def copyBuilder(): ObjectRemovePropertyOperation.Builder = new ObjectRemovePropertyOperation.Builder(path, noOp, property, oldValue)

  def transform(other: ObjectOperation): (DiscreteOperation, DiscreteOperation) = other match {
    case other: ObjectSetPropertyOperation => ObjectOperationTransformer.transformRemovePropertySetProperty(this, other)
    case other: ObjectRemovePropertyOperation => ObjectOperationTransformer.transformRemovePropertyRemoveProperty(this, other)
    case other: ObjectSetOperation => ObjectOperationTransformer.transformRemovePropertySet(this, other)
  }
}

object ObjectRemovePropertyOperation {
  class Builder(path: List[Any], noOp: scala.Boolean, var property: String, var oldValue: JsonNode) extends DiscreteOperation.Builder(path, noOp) {
    def build(): ObjectRemovePropertyOperation = ObjectRemovePropertyOperation(path, noOp, property, oldValue)
  }
}

case class ObjectSetOperation(override val path: List[Any], override val noOp: Boolean, oldValue: JsonNode, newValue: JsonNode) extends DiscreteOperation(path, noOp) with ObjectOperation {

  def invert(): DiscreteOperation = ObjectSetOperation(path, noOp, newValue, oldValue)

  def copyBuilder(): ObjectSetOperation.Builder = new ObjectSetOperation.Builder(path, noOp, oldValue, newValue)

  def transform(other: ObjectOperation): (DiscreteOperation, DiscreteOperation) = other match {
    case other: ObjectSetPropertyOperation => ObjectOperationTransformer.transformSetSetProperty(this, other)
    case other: ObjectRemovePropertyOperation => ObjectOperationTransformer.transformSetRemoveProperty(this, other)
    case other: ObjectSetOperation => ObjectOperationTransformer.transformSetSet(this, other)
  }
}

object ObjectSetOperation {
  class Builder(path: List[Any], noOp: scala.Boolean, var oldValue: JsonNode, var newValue: JsonNode) extends DiscreteOperation.Builder(path, noOp) {
    def build(): ObjectSetOperation = ObjectSetOperation(path, noOp, oldValue, newValue)
  }
}

