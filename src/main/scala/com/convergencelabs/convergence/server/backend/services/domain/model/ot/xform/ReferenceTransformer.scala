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

package com.convergencelabs.convergence.server.backend.services.domain.model.ot.xform

import com.convergencelabs.convergence.server.backend.services.domain.model.ot.{CompoundOperation, DiscreteOperation, Operation}
import com.convergencelabs.convergence.server.model.domain.model.ModelReferenceValues

import scala.util.control.Breaks._

class ReferenceTransformer(tfr: TransformationFunctionRegistry) {

  def transform[V <: ModelReferenceValues](op: Operation, valueId: String, values: V): Option[V] = {
    op match {
      case c: CompoundOperation =>
        transform(c, valueId, values)
      case d: DiscreteOperation =>
        transform[V](d, valueId, values)
    }
  }

  private[this] def transform[V <: ModelReferenceValues](op: CompoundOperation, valueId: String, values: V): Option[V] = {
    var result: Option[V] = Some(values)

    breakable {
      op.operations.foreach { op =>
        if (result.isEmpty) {
          break()
        }
        result = result.flatMap(v => transform(op, valueId, v))
      }
    }

    result
  }

  private[this] def transform[V <: ModelReferenceValues](op: DiscreteOperation, referenceOwnerValueId: String, values: V): Option[V] = {
    if (op.noOp) {
      Some(values)
    } else if (referenceOwnerValueId != op.id) {
      // The operation does not target the real time value
      // that this reference is for.
      Some(values)
    } else {
      tfr.getReferenceTransformationFunction(op, values) match {
        case Some(tf) => tf.transform(op, values)
        case None => throw new IllegalArgumentException(
          s"No reference transformation function found for operation and reference pair (${op.getClass.getSimpleName},${values.getClass.getSimpleName})")
      }
    }
  }
}
