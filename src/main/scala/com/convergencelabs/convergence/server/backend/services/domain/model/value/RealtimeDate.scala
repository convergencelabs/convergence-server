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

import java.time.Instant

import com.convergencelabs.convergence.server.backend.services.domain.model.ot.{AppliedDateOperation, AppliedDateSetOperation, DateSetOperation, DiscreteOperation}
import com.convergencelabs.convergence.server.model.domain.model.DateValue

import scala.util.{Failure, Success, Try}

private[model] final class RealtimeDate(value: DateValue,
                                        parent: Option[RealtimeContainerValue],
                                        parentField: Option[Any])
  extends RealtimeValue(value.id, parent, parentField, List()) {

  private[this] var date = value.value

  def data(): Instant = {
    date
  }

  def dataValue(): DateValue = {
    DateValue(id, date)
  }

  protected def processValidatedOperation(op: DiscreteOperation): Try[AppliedDateOperation] = {
    op match {
      case value: DateSetOperation =>
        this.processSetOperation(value)
      case _ =>
        Failure(new IllegalArgumentException("Invalid operation type fore RealTimeDate: " + op))
    }
  }

  private[this] def processSetOperation(op: DateSetOperation): Try[AppliedDateSetOperation] = {
    val DateSetOperation(id, noOp, value) = op

    val oldValue = data()
    date = value

    Success(AppliedDateSetOperation(id, noOp, value, Some(oldValue)))
  }
}
