package com.convergencelabs.server.domain.model

import scala.util.Try
import com.convergencelabs.server.domain.model.ot.DiscreteOperation
import scala.util.Failure
import scala.util.Success

abstract class RealTimeContainerValue(
  private[this] val id: String,
  private[this] val model: RealTimeModel,
  private[this] val parent: Option[RealTimeContainerValue],
  private[this] val parentField: Option[Any],
  private[this] val validReferenceTypes: List[ReferenceType.Value])
    extends RealTimeValue(id, model, parent, parentField, validReferenceTypes) {

  def valueAt(path: List[Any]): Option[RealTimeValue]

  protected def child(childPath: Any): Try[Option[RealTimeValue]]

  override def detach(): Unit = {
    this.detachChildren()
    super.detach()
  }
  
  def detachChildren(): Unit;

  def children(): List[RealTimeValue]

  override def sessionDisconnected(sessionId: String): Unit = {
    this.children.foreach { child => child.sessionDisconnected(sessionId) }
    super.sessionDisconnected(sessionId)
  }
  
  
}
