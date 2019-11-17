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

import com.convergencelabs.convergence.server.domain.model.data.BooleanValue
import com.convergencelabs.convergence.server.domain.model.ot.{AppliedBooleanOperation, AppliedBooleanSetOperation, BooleanSetOperation, DiscreteOperation}

import scala.util.{Failure, Success, Try}

class RealTimeBoolean(private[this] val value: BooleanValue,
                      private[this] val parent: Option[RealTimeContainerValue],
                      private[this] val parentField: Option[Any])
  extends RealTimeValue(value.id, parent, parentField, List()) {

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
