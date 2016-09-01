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
import com.convergencelabs.server.domain.model.reference.PropertyRemoveAware
import com.convergencelabs.server.domain.model.ot.AppliedObjectOperation
import com.convergencelabs.server.domain.model.ot.AppliedObjectAddPropertyOperation
import com.convergencelabs.server.domain.model.ot.AppliedObjectRemovePropertyOperation
import com.convergencelabs.server.domain.model.data.DataValue
import com.convergencelabs.server.domain.model.ot.AppliedObjectSetPropertyOperation
import com.convergencelabs.server.domain.model.ot.AppliedObjectSetOperation

class RealTimeObject(
  private[this] val value: ObjectValue,
  private[this] val model: RealTimeModel,
  private[this] val parent: Option[RealTimeContainerValue],
  private[this] val parentField: Option[Any])
    extends RealTimeContainerValue(value.id, model, parent, parentField, List(ReferenceType.Property)) {

  var childValues: Map[String, RealTimeValue] = value.children.map {
    case (k, v) => (k, this.model.createValue(v, Some(this), Some(k)))
  }.toMap

  def children(): List[RealTimeValue] = {
    childValues.values.toList
  }

  def valueAt(path: List[Any]): Option[RealTimeValue] = {
    path match {
      case Nil =>
        Some(this)
      case (prop: String) :: Nil =>
        this.childValues.get(prop)
      case (prop: String) :: rest =>
        this.childValues.get(prop).flatMap {
          case child: RealTimeContainerValue => child.valueAt(rest)
          case _ => None
        }
      case _ =>
        None
    }

  }

  def data(): Map[String, _] = {
    childValues.mapValues { child => child.data() }
  }
  
  def dataValue(): ObjectValue = {
    ObjectValue(id, childValues.mapValues { child => child.dataValue()})
  }

  protected def child(childPath: Any): Try[Option[RealTimeValue]] = {
    childPath match {
      case prop: String =>
        Success(this.childValues.get(prop))
      case _ =>
        Failure(new IllegalArgumentException("Child path must be a string for a RealTimeObject"))
    }
  }

  def processOperation(op: DiscreteOperation): Try[AppliedObjectOperation] = Try {
    op match {
      case add: ObjectAddPropertyOperation => processAddPropertyOperation(add)
      case remove: ObjectRemovePropertyOperation => processRemovePropertyOperation(remove)
      case set: ObjectSetPropertyOperation => processSetPropertyOperation(set)
      case value: ObjectSetOperation => processSetValueOperation(value)
      case _ => throw new IllegalArgumentException("Invalid operation for RealTimeObject: " + op)
    }
  }

  def processAddPropertyOperation(op: ObjectAddPropertyOperation): AppliedObjectAddPropertyOperation = {
    val ObjectAddPropertyOperation(id, noOp, property, value) = op
    if (childValues.contains(property)) {
      new IllegalArgumentException(s"Object already contains property ${property}")
    }
    val child = this.model.createValue(value, Some(this), Some(property))
    this.childValues = this.childValues + (property -> child)
    
    AppliedObjectAddPropertyOperation(id, noOp, property, value)
  }

  def processRemovePropertyOperation(op: ObjectRemovePropertyOperation): AppliedObjectRemovePropertyOperation = {
    val ObjectRemovePropertyOperation(id, noOp, property) = op
    if (!childValues.contains(property)) {
      new IllegalArgumentException(s"Object does not contain property ${property}")
    }

    val child = this.childValues(property)
    childValues = this.childValues - property

    this.referenceManager.referenceMap.getAll().foreach {
      case x: PropertyRemoveAware => x.handlePropertyRemove(op.property)
    }
    
    AppliedObjectRemovePropertyOperation(id, noOp, property, Some(child.dataValue()))
  }

  def processSetPropertyOperation(op: ObjectSetPropertyOperation): AppliedObjectSetPropertyOperation = {
    val ObjectSetPropertyOperation(id, noOp, property, value) = op
    if (!childValues.contains(property)) {
      new IllegalArgumentException(s"Object does not contain property ${property}")
    }

    val oldChild = childValues(op.property)
    val child = this.model.createValue(op.value, Some(this), Some(property))
    childValues = childValues + (property -> child)
    
    AppliedObjectSetPropertyOperation(id, noOp, property, value, Some(oldChild.dataValue()))
  }

  def processSetValueOperation(op: ObjectSetOperation): AppliedObjectSetOperation = {
    val ObjectSetOperation(id, noOp, value) = op
    val oldValue = dataValue()
    childValues = op.value.map {
      case (k, v) => (k, this.model.createValue(v, Some(this), Some(k)))
    }.toMap
    AppliedObjectSetOperation(id, noOp, value, Some(oldValue.children))
  }
}
