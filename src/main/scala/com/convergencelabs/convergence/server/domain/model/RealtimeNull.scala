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

import com.convergencelabs.convergence.server.domain.model.data.NullValue
import com.convergencelabs.convergence.server.domain.model.ot.{AppliedDiscreteOperation, DiscreteOperation}

import scala.util.{Failure, Try}

class RealtimeNull(private[this] val value: NullValue,
                   private[this] val parent: Option[RealtimeContainerValue],
                   private[this] val parentField: Option[Any])
  extends RealtimeValue(value.id, parent, parentField, List()) {

  def data(): Null = {
    null // scalastyle:ignore null
  }

  def dataValue(): NullValue = {
    value
  }

  protected def processValidatedOperation(op: DiscreteOperation): Try[AppliedDiscreteOperation] = {
    Failure(new IllegalArgumentException("Invalid operation type for RealTimeNull: " + op));
  }
}
