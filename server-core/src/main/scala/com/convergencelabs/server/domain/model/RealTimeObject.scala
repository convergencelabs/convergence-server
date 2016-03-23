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
import com.convergencelabs.server.domain.model.data.ObjectValue

class RealTimeObject(
  private[this] val value: ObjectValue,
  private[this] val model: RealTimeModel,
  private[this] val parent: Option[RealTimeContainerValue],
  private[this] val parentField: Option[Any])
    extends RealTimeContainerValue(value.id, model, parent, parentField) {

  var children: Map[String, RealTimeValue] = value.value.map {
    case (k, v) => (k, this.model.createValue(v, Some(this), Some(k)))
  }.toMap

  def valueAt(path: List[Any]): Option[RealTimeValue] = {
    path match {
      case Nil =>
        Some(this)
      case (prop: String) :: Nil =>
        this.children.get(prop)
      case (prop: String) :: rest =>
        this.children.get(prop).flatMap {
          case child: RealTimeContainerValue => child.valueAt(rest)
          case _ => None
        }
      case _ =>
        None
    }

  }

  def data(): Map[String, _] = {
    children.mapValues { child => child.data() }
  }

  protected def child(childPath: Any): Try[Option[RealTimeValue]] = {
    childPath match {
      case prop: String =>
        Success(this.children.get(prop))
      case _ =>
        Failure(new IllegalArgumentException("Child path must be a string for a RealTimeObject"))
    }
  }

  def processOperation(op: DiscreteOperation): Try[Unit] = Try {
    op match {
      case add: ObjectAddPropertyOperation => processAddPropertyOperation(add)
      case remove: ObjectRemovePropertyOperation => processRemovePropertyOperation(remove)
      case set: ObjectSetPropertyOperation => processSetPropertyOperation(set)
      case value: ObjectSetOperation => processSetValueOperation(value)
      case _ => throw new IllegalArgumentException("Invalid operation for RealTimeObject: " + op)
    }
  }

  def processAddPropertyOperation(op: ObjectAddPropertyOperation): Try[Unit] = Try {
    if (this.children.contains(op.property)) {
      new IllegalArgumentException(s"Object already contains property ${op.property}")
    }
    val child = this.model.createValue(op.value, Some(this), Some(op.property))
    this.children = this.children + (op.property -> child)
  }

  def processRemovePropertyOperation(op: ObjectRemovePropertyOperation): Try[Unit] = Try {
    if (!this.children.contains(op.property)) {
      new IllegalArgumentException(s"Object does not contain property ${op.property}")
    }

    val child = this.children(op.property)
    this.children = this.children - op.property
  }

  def processSetPropertyOperation(op: ObjectSetPropertyOperation): Try[Unit] = Try {
    if (!this.children.contains(op.property)) {
      new IllegalArgumentException(s"Object does not contain property ${op.property}")
    }

    val oldChild = this.children(op.property)
    val child = this.model.createValue(op.value, Some(this), Some(op.property))
    this.children = this.children + (op.property -> child)
  }

  def processSetValueOperation(op: ObjectSetOperation): Try[Unit] = Try {
    this.children = op.value.map {
      case (k, v) => (k, this.model.createValue(v, Some(this), Some(k)))
    }.toMap
  }
  
  def detachChildren(): Unit = {
    this.children.values.foreach({child => child.detach()})
  }
  
  def processReferenceEvent(event: ModelReferenceEvent): Try[Unit] = {
    Success(())
  }
}