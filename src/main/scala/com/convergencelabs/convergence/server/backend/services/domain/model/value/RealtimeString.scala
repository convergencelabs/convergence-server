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

import com.convergencelabs.convergence.server.backend.services.domain.model.ot._
import com.convergencelabs.convergence.server.backend.services.domain.model.reference.PositionalSpliceAwareReference
import com.convergencelabs.convergence.server.model.domain.model.{IndexReferenceValues, RangeReferenceValues, StringValue}

import scala.util.{Failure, Success, Try}

private[model] final class RealtimeString(value: StringValue,
                                          parent: Option[RealtimeContainerValue],
                                          parentField: Option[Any])
  extends RealtimeValue(
    value.id,
    parent,
    parentField,
    List(classOf[IndexReferenceValues], classOf[RangeReferenceValues])) {

  private[this] var string = value.value

  def data(): String = {
    this.string
  }

  def dataValue(): StringValue = {
    StringValue(id, string)
  }

  protected def processValidatedOperation(op: DiscreteOperation): Try[AppliedStringOperation] = {
    op match {
      case splice: StringSpliceOperation =>
        this.processSpliceOperation(splice)
      case value: StringSetOperation =>
        this.processSetOperation(value)
      case op =>
        Failure(new IllegalArgumentException("Invalid operation type for RealTimeString: " + op))
    }
  }

  private[this] def processSpliceOperation(op: StringSpliceOperation): Try[AppliedStringSpliceOperation] = {
    val StringSpliceOperation(id, noOp, index, deleteCount, insertValue) = op

    if (this.string.length < index || index < 0 || index + deleteCount > this.string.length) {
      Failure(new IllegalArgumentException("Index out of bounds: " + index))
    } else {
      val oldValue = this.string.substring(index, index + deleteCount)
      this.string = this.string.patch(index, insertValue, deleteCount)

      this.referenceManager.referenceMap().getAll.foreach {
        case x: PositionalSpliceAwareReference => x.handlePositionalSplice(index, deleteCount, insertValue.length)
        case _ => // no-op
      }

      Success(AppliedStringSpliceOperation(id, noOp, index, oldValue.length, Some(oldValue), insertValue))
    }
  }

  private[this] def processSetOperation(op: StringSetOperation): Try[AppliedStringSetOperation] = {
    val StringSetOperation(id, noOp, value) = op

    val oldValue = string
    this.string = value
    this.referenceManager.referenceMap().getAll.foreach { x => x.handleModelValueSet() }

    Success(AppliedStringSetOperation(id, noOp, value, Some(oldValue)))
  }
}
