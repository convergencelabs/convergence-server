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

package com.convergencelabs.convergence.server.domain.model.ot.xform

import com.convergencelabs.convergence.server.domain.model.ReferenceValue
import com.convergencelabs.convergence.server.domain.model.ot.{CompoundOperation, DiscreteOperation, Operation, TransformationFunctionRegistry}

import scala.util.control.Breaks._

class ReferenceTransformer(private[this] val tfr: TransformationFunctionRegistry) {

  def transform(op: Operation, setReference: ReferenceValue): Option[ReferenceValue] = {
    op match {
      case c: CompoundOperation =>
        transform(c, setReference)
      case d: DiscreteOperation =>
        transform(d, setReference)
    }
  }

  private[this] def transform(op: CompoundOperation, setReference: ReferenceValue): Option[ReferenceValue] = {
    var result: Option[ReferenceValue] = Some(setReference)

    breakable {
      op.operations.foreach { op =>
        if (result.isEmpty) {
          break()
        }
        result = result.flatMap { reference => transform(op, reference) }
      }
    }

    result
  }

  private[this] def transform(op: DiscreteOperation, setReference: ReferenceValue): Option[ReferenceValue] = {
    tfr.getReferenceTransformationFunction(op, setReference.referenceType) match {
      case Some(tf) => tf.transform(op, setReference)
      case None => throw new IllegalArgumentException(
        s"No reference transformation function found for operation and reference pair (${op.getClass.getName},${setReference.referenceType})")
    }
  }
}
