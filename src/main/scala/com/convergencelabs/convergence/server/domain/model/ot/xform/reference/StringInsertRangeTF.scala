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

package com.convergencelabs.convergence.server.domain.model.ot.xform.reference

import com.convergencelabs.convergence.server.domain.model.ot.ReferenceTransformationFunction
import com.convergencelabs.convergence.server.domain.model.ot.StringInsertOperation
import com.convergencelabs.convergence.server.domain.model.ot.xform.IndexTransformer
import com.convergencelabs.convergence.server.domain.model.ReferenceValue

object StringInsertRangeTF extends ReferenceTransformationFunction[StringInsertOperation] {
  def transform(op: StringInsertOperation, setReference: ReferenceValue): Option[ReferenceValue] = {
    val ranges = setReference.values.asInstanceOf[List[(Int, Int)]]
    
    val xFormedRanges = ranges map { range =>
      val xFormed = IndexTransformer.handleInsert(List(range._1, range._2), op.index, op.value.length)
      (xFormed(0), xFormed(1))
    }

    Some(setReference.copy(values = xFormedRanges))
  }
}
