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

package com.convergencelabs.convergence.server.backend.services.domain.model.value

import com.convergencelabs.convergence.server.backend.services.domain.model.ot.{AppliedBooleanOperation, AppliedBooleanSetOperation, BooleanSetOperation, DiscreteOperation}
import com.convergencelabs.convergence.server.model.domain.model.BooleanValue

import scala.util.{Failure, Success, Try}

private[model] final class RealtimeBoolean(value: BooleanValue,
                                           parent: Option[RealtimeContainerValue],
                                           parentField: Option[Any])
  extends RealtimeValue(value.id, parent, parentField, List()) {

  private[this] var boolean = value.value

  def data(): Boolean = {
    boolean
  }

  def dataValue(): BooleanValue = {
    BooleanValue(id, boolean)
  }

  protected def processValidatedOperation(op: DiscreteOperation): Try[AppliedBooleanOperation] = {
    op match {
      case value: BooleanSetOperation =>
        this.processSetOperation(value)
      case _ =>
        Failure(new IllegalArgumentException("Invalid operation type for RealTimeBoolean: " + op))
    }
  }

  private[this] def processSetOperation(op: BooleanSetOperation): Try[AppliedBooleanSetOperation] = {
    val BooleanSetOperation(id, noOp, value) = op

    val oldValue = data()
    boolean = value

    Success(AppliedBooleanSetOperation(id, noOp, value, Some(oldValue)))
  }
}
