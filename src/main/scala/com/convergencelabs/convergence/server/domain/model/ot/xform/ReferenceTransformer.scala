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

import com.convergencelabs.convergence.server.domain.model.ot.{CompoundOperation, DiscreteOperation, Operation, TransformationFunctionRegistry}
import com.convergencelabs.convergence.server.domain.model.{ModelReferenceValues, ReferenceValue}

import scala.util.control.Breaks._

class ReferenceTransformer(tfr: TransformationFunctionRegistry) {

  def transform[V <: ModelReferenceValues](op: Operation, values: V): Option[V] = {
    op match {
      case c: CompoundOperation =>
        transform(c, values)
      case d: DiscreteOperation =>
        transform[V](d, values)
    }
  }

  private[this] def transform[V <: ModelReferenceValues](op: CompoundOperation, values: V): Option[V] = {
    var result: Option[V] = Some(values)

    breakable {
      op.operations.foreach { op =>
        if (result.isEmpty) {
          break()
        }
        result = result.flatMap(v => transform(op, v))
      }
    }

    result
  }

  private[this] def transform[V <: ModelReferenceValues](op: DiscreteOperation, values: V): Option[V] = {
    tfr.getReferenceTransformationFunction(op, values) match {
      case Some(tf) => tf.transform(op, values)
      case None => throw new IllegalArgumentException(
        s"No reference transformation function found for operation and reference pair (${op.getClass.getSimpleName},${values.getClass.getSimpleName})")
    }
  }
}
