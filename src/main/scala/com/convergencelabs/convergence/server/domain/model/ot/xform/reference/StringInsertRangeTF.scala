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

import com.convergencelabs.convergence.server.domain.model.RangeReferenceValues
import com.convergencelabs.convergence.server.domain.model.ot.xform.IndexTransformer
import com.convergencelabs.convergence.server.domain.model.ot.{ReferenceTransformationFunction, StringInsertOperation}
import com.convergencelabs.convergence.server.domain.model.reference.RangeReference

object StringInsertRangeTF extends ReferenceTransformationFunction[StringInsertOperation, RangeReferenceValues] {
  def transform(op: StringInsertOperation, values: RangeReferenceValues): Option[RangeReferenceValues] = {
    val xFormedRanges = values.values map { range =>
      val xFormed = IndexTransformer.handleInsert(List(range.to, range.from), op.index, op.value.length)
      RangeReference.Range(xFormed.head, xFormed.last)
    }
    Some(RangeReferenceValues(xFormedRanges))
  }
}
