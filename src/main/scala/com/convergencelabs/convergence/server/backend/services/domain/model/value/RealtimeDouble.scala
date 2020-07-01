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

package com.convergencelabs.convergence.server.backend.services.domain.model

import com.convergencelabs.convergence.server.backend.services.domain.model.ot._
import com.convergencelabs.convergence.server.model.domain.model.DoubleValue

import scala.util.{Failure, Success, Try}

class RealtimeDouble(private[this] val value: DoubleValue,
                     private[this] val parent: Option[RealtimeContainerValue],
                     private[this] val parentField: Option[Any])
  extends RealtimeValue(value.id, parent, parentField, List()) {

  var double: Double = value.value

  def data(): Double = {
    this.double
  }

  def dataValue(): DoubleValue = {
    DoubleValue(id, double)
  }

  protected def processValidatedOperation(op: DiscreteOperation): Try[AppliedNumberOperation] = {
    op match {
      case add: NumberAddOperation =>
        this.processAddOperation(add)
      case value: NumberSetOperation =>
        this.processSetOperation(value)
      case _ =>
        Failure(new IllegalArgumentException("Invalid operation type for RealTimeDouble: " + op))
    }
  }

  private[this] def processAddOperation(op: NumberAddOperation): Try[AppliedNumberAddOperation] = {
    val NumberAddOperation(id, noOp, value) = op
    double = double + value

    Success(AppliedNumberAddOperation(id, noOp, value))
  }

  private[this] def processSetOperation(op: NumberSetOperation): Try[AppliedNumberSetOperation] = {
    val NumberSetOperation(id, noOp, value) = op
    val oldValue = double
    double = value

    Success(AppliedNumberSetOperation(id, noOp, value, Some(oldValue)))
  }
}
