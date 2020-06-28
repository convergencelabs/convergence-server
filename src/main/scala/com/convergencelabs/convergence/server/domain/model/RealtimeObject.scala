/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is part of the Convergence Server, which is released under
 * the terms of the GNU General Public License version 3 (GPLv3). A copy
 * of the GPLv3 should have been provided along with this file, typically
 * located in the "LICENSE" file, which is part of this source code package.
 * Alternatively, see <https://www.gnu.org/licenses/gpl-3.0.html> for the
 * full text of the GPLv3 license, if it was not provided.
 */

package com.convergencelabs.convergence.server.domain.model

import com.convergencelabs.convergence.server.domain.model.data.ObjectValue
import com.convergencelabs.convergence.server.domain.model.ot._
import com.convergencelabs.convergence.server.domain.model.reference.PropertyRemoveAwareReference

import scala.util.{Failure, Success, Try}

class RealtimeObject(private[this] val value: ObjectValue,
                     private[this] val parent: Option[RealtimeContainerValue],
                     private[this] val parentField: Option[Any],
                     private[this] val valueFactory: RealtimeValueFactory)
  extends RealtimeContainerValue(value.id, parent, parentField, List(classOf[PropertyReferenceValues])) {

  private[this] var childValues: Map[String, RealtimeValue] = value.children.map {
    case (k, v) => (k, this.valueFactory.createValue(v, Some(this), Some(k)))
  }

  def children: List[RealtimeValue] = {
    childValues.values.toList
  }

  def valueAt(path: List[Any]): Option[RealtimeValue] = {
    path match {
      case Nil =>
        Some(this)
      case (prop: String) :: Nil =>
        this.childValues.get(prop)
      case (prop: String) :: rest =>
        this.childValues.get(prop).flatMap {
          case child: RealtimeContainerValue => child.valueAt(rest)
          case _ => None
        }
      case _ =>
        None
    }
  }

  def data(): Map[String, _] = {
    childValues.map { case (k, v) => k -> v.data() }
  }

  def dataValue(): ObjectValue = {
    ObjectValue(id, childValues.map { case (k, v) => k -> v.dataValue() })
  }

  def child(childPath: Any): Try[Option[RealtimeValue]] = {
    childPath match {
      case prop: String =>
        Success(this.childValues.get(prop))
      case _ =>
        Failure(new IllegalArgumentException("Child path must be a string for a RealTimeObject"))
    }
  }

  protected def processValidatedOperation(op: DiscreteOperation): Try[AppliedObjectOperation] = {
    op match {
      case add: ObjectAddPropertyOperation =>
        processAddPropertyOperation(add)
      case remove: ObjectRemovePropertyOperation =>
        processRemovePropertyOperation(remove)
      case set: ObjectSetPropertyOperation =>
        processSetPropertyOperation(set)
      case value: ObjectSetOperation =>
        processSetValueOperation(value)
      case _ =>
        Failure(new IllegalArgumentException("Invalid operation type for RealTimeObject: " + op))
    }
  }

  private[this] def processAddPropertyOperation(op: ObjectAddPropertyOperation): Try[AppliedObjectAddPropertyOperation] = {
    val ObjectAddPropertyOperation(id, noOp, property, value) = op
    if (childValues.contains(property)) {
      Failure(new IllegalArgumentException(s"Object already contains property '$property''"))
    } else {
      val child = this.valueFactory.createValue(value, Some(this), Some(property))
      this.childValues = this.childValues + (property -> child)

      Success(AppliedObjectAddPropertyOperation(id, noOp, property, value))
    }
  }

  private[this] def processRemovePropertyOperation(op: ObjectRemovePropertyOperation): Try[AppliedObjectRemovePropertyOperation] = {
    val ObjectRemovePropertyOperation(id, noOp, property) = op
    if (!childValues.contains(property)) {
      Failure(new IllegalArgumentException(s"Object does not contain property '$property''"))
    } else {
      val child = this.childValues(property)
      childValues = this.childValues - property

      this.referenceManager.referenceMap().getAll.foreach {
        case x: PropertyRemoveAwareReference => x.handlePropertyRemove(op.property)
      }

      child.detach()

      Success(AppliedObjectRemovePropertyOperation(id, noOp, property, Some(child.dataValue())))
    }
  }

  private[this] def processSetPropertyOperation(op: ObjectSetPropertyOperation): Try[AppliedObjectSetPropertyOperation] = {
    val ObjectSetPropertyOperation(id, noOp, property, value) = op
    if (!childValues.contains(property)) {
      Failure(new IllegalArgumentException(s"Object does not contain property '$property'"))
    } else {
      val oldChild = childValues(op.property)
      val child = this.valueFactory.createValue(op.value, Some(this), Some(property))
      childValues = childValues + (property -> child)

      child.detach()

      Success(AppliedObjectSetPropertyOperation(id, noOp, property, value, Some(oldChild.dataValue())))
    }
  }

  private[this] def processSetValueOperation(op: ObjectSetOperation): Try[AppliedObjectSetOperation] = {
    val ObjectSetOperation(id, noOp, value) = op
    val oldValue = dataValue()

    this.detachChildren()

    childValues = op.value.map {
      case (k, v) => (k, this.valueFactory.createValue(v, Some(this), Some(k)))
    }

    Success(AppliedObjectSetOperation(id, noOp, value, Some(oldValue.children)))
  }

  override def detachChildren(): Unit = {
    this.childValues.foreach {
      case (_, v) => v.detach()
    }
  }
}
