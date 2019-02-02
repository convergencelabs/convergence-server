package com.convergencelabs.server.domain.model

import scala.util.Try
import com.convergencelabs.server.domain.DomainUserSessionId

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

  override def sessionDisconnected(session: DomainUserSessionId): Unit = {
    this.children.foreach { child => child.sessionDisconnected(session) }
    super.sessionDisconnected(session)
  }
}
