package com.convergencelabs.server.domain.model

import scala.util.Try

abstract class RealTimeContainerValue(
  private[this] val id: String,
  private[this] val parent: Option[RealTimeContainerValue],
  private[this] val parentField: Option[Any],
  private[this] val validReferenceTypes: List[ReferenceType.Value])
  extends RealTimeValue(id, parent, parentField, validReferenceTypes) {

  def valueAt(path: List[Any]): Option[RealTimeValue]

  def child(childPath: Any): Try[Option[RealTimeValue]]

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
