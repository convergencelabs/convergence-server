/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

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
