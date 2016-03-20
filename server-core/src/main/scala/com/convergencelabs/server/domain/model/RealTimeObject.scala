package com.convergencelabs.server.domain.model

import com.convergencelabs.server.domain.model.ot.ObjectAddPropertyOperation
import com.convergencelabs.server.domain.model.ot.ObjectSetOperation
import com.convergencelabs.server.domain.model.ot.ObjectSetPropertyOperation
import com.convergencelabs.server.domain.model.ot.ObjectRemovePropertyOperation
import scala.util.Failure
import scala.util.Success
import scala.util.Try
import org.json4s.JsonAST.JObject
import com.convergencelabs.server.domain.model.ot.DiscreteOperation

class RealTimeObject(
  private[this] val model: RealTimeModel,
  private[this] val parent: Option[RealTimeContainerValue],
  private[this] val parentField: Option[Any],
  private[this] val obj: JObject)
    extends RealTimeContainerValue(model, parent, parentField) {

  var children: Map[String, RealTimeValue] = obj.obj.map {
    case (k, v) => (k, this.model.createValue(Some(this), Some(k), v))
  }.toMap

  def value(): Map[String, _] = {
    children.mapValues { child => child.value() }
  }
  
  def processOperation(op: DiscreteOperation): Try[Unit] = Try {
    op match {
      case add: ObjectAddPropertyOperation => processAddPropertyOperation(add);
      case remove: ObjectRemovePropertyOperation => processRemovePropertyOperation(remove);
      case set: ObjectSetPropertyOperation => processSetPropertyOperation(set);
      case value: ObjectSetOperation => processSetValueOperation(value);
      case _ => throw new IllegalArgumentException("Invalid operation for RealTimeObject");
    }
  }

  def processAddPropertyOperation(op: ObjectAddPropertyOperation): Try[Unit] = Try {
    if (this.value.contains(op.property)) {
      new IllegalArgumentException(s"Object already contains property ${op.property}")
    }
    val child = this.model.createValue(Some(this), Some(op.property), op.value)
    this.children = this.children + (op.property -> child)
  }

  def processRemovePropertyOperation(op: ObjectRemovePropertyOperation): Try[Unit] = Try {
    if (!this.value.contains(op.property)) {
      new IllegalArgumentException(s"Object does not contain property ${op.property}")
    }
    
    val child = this.children(op.property)
    this.children = this.children - op.property
  }

  def processSetPropertyOperation(op: ObjectSetPropertyOperation): Try[Unit] = Try {
    if (!this.value.contains(op.property)) {
      new IllegalArgumentException(s"Object does not contain property ${op.property}")
    }

    val oldChild = this.children(op.property)
    val child = this.model.createValue(Some(this), Some(op.property), op.value)
    this.children = this.children + (op.property -> child)
  }

  def processSetValueOperation(op: ObjectSetOperation): Try[Unit] = Try {
    this.children = op.value.obj.map {
      case (k, v) => (k, this.model.createValue(Some(this), Some(k), v))
    }.toMap
  }
}