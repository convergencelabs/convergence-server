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

import scala.util.Try
import com.convergencelabs.convergence.server.domain.DomainUserSessionId

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
