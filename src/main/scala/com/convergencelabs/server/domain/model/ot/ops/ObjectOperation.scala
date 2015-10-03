package com.convergencelabs.server.domain.model.ot.ops

import com.convergencelabs.server.domain.model.ot.xform.ObjectOperationTransformer
import org.json4s.JsonAST.JValue
import org.json4s.JsonAST.JObject

// FIXME xforms handling add.

sealed trait ObjectOperation {
  def transform(other: ObjectOperation): (DiscreteOperation, DiscreteOperation)
}

case class ObjectSetPropertyOperation(override val path: List[Any], override val noOp: Boolean, property: String, newValue: JValue) extends DiscreteOperation(path, noOp) with ObjectOperation {

  def copyBuilder(): ObjectSetPropertyOperation.Builder = new ObjectSetPropertyOperation.Builder(path, noOp, property, newValue)

  def transform(other: ObjectOperation): (DiscreteOperation, DiscreteOperation) = other match {
    case other: ObjectAddPropertyOperation => ObjectOperationTransformer.transformSetPropertyAddProperty(this, other)
    case other: ObjectSetPropertyOperation => ObjectOperationTransformer.transformSetPropertySetProperty(this, other)
    case other: ObjectRemovePropertyOperation => ObjectOperationTransformer.transformSetPropertyRemoveProperty(this, other)
    case other: ObjectSetOperation => ObjectOperationTransformer.transformSetPropertySet(this, other)
  }
}

object ObjectSetPropertyOperation {
  class Builder(path: List[Any], noOp: scala.Boolean, var property: String, var newValue: JValue) extends DiscreteOperation.Builder(path, noOp) {
    def build(): ObjectSetPropertyOperation = ObjectSetPropertyOperation(path, noOp, property, newValue)
  }
}

case class ObjectAddPropertyOperation(override val path: List[Any], override val noOp: Boolean, property: String, newValue: JValue) extends DiscreteOperation(path, noOp) with ObjectOperation {

  def copyBuilder(): ObjectAddPropertyOperation.Builder = new ObjectAddPropertyOperation.Builder(path, noOp, property, newValue)

  def transform(other: ObjectOperation): (DiscreteOperation, DiscreteOperation) = other match {
    case other: ObjectAddPropertyOperation => ObjectOperationTransformer.transformAddPropertyAddProperty(this, other)
    case other: ObjectSetPropertyOperation => ObjectOperationTransformer.transformAddPropertySetProperty(this, other)
    case other: ObjectRemovePropertyOperation => ObjectOperationTransformer.transformAddPropertyRemoveProperty(this, other)
    case other: ObjectSetOperation => ObjectOperationTransformer.transformAddPropertySet(this, other)
  }
}

object ObjectAddPropertyOperation {
  class Builder(path: List[Any], noOp: scala.Boolean, var property: String, var newValue: JValue) extends DiscreteOperation.Builder(path, noOp) {
    def build(): ObjectAddPropertyOperation = ObjectAddPropertyOperation(path, noOp, property, newValue)
  }
}

case class ObjectRemovePropertyOperation(override val path: List[Any], override val noOp: Boolean, property: String) extends DiscreteOperation(path, noOp) with ObjectOperation {

  def copyBuilder(): ObjectRemovePropertyOperation.Builder = new ObjectRemovePropertyOperation.Builder(path, noOp, property)

  def transform(other: ObjectOperation): (DiscreteOperation, DiscreteOperation) = other match {
    case other: ObjectAddPropertyOperation => ObjectOperationTransformer.transformRemovePropertyAddProperty(this, other)
    case other: ObjectSetPropertyOperation => ObjectOperationTransformer.transformRemovePropertySetProperty(this, other)
    case other: ObjectRemovePropertyOperation => ObjectOperationTransformer.transformRemovePropertyRemoveProperty(this, other)
    case other: ObjectSetOperation => ObjectOperationTransformer.transformRemovePropertySet(this, other)
  }
}

object ObjectRemovePropertyOperation {
  class Builder(path: List[Any], noOp: scala.Boolean, var property: String) extends DiscreteOperation.Builder(path, noOp) {
    def build(): ObjectRemovePropertyOperation = ObjectRemovePropertyOperation(path, noOp, property)
  }
}

case class ObjectSetOperation(override val path: List[Any], override val noOp: Boolean, newValue: JObject) extends DiscreteOperation(path, noOp) with ObjectOperation {

  def copyBuilder(): ObjectSetOperation.Builder = new ObjectSetOperation.Builder(path, noOp, newValue)

  def transform(other: ObjectOperation): (DiscreteOperation, DiscreteOperation) = other match {
    case other: ObjectAddPropertyOperation => ObjectOperationTransformer.transformSetAddProperty(this, other)
    case other: ObjectSetPropertyOperation => ObjectOperationTransformer.transformSetSetProperty(this, other)
    case other: ObjectRemovePropertyOperation => ObjectOperationTransformer.transformSetRemoveProperty(this, other)
    case other: ObjectSetOperation => ObjectOperationTransformer.transformSetSet(this, other)
  }
}

object ObjectSetOperation {
  class Builder(path: List[Any], noOp: scala.Boolean, var newValue: JObject) extends DiscreteOperation.Builder(path, noOp) {
    def build(): ObjectSetOperation = ObjectSetOperation(path, noOp, newValue)
  }
}

