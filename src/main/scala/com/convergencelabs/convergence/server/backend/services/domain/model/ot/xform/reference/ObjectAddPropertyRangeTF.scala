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

package com.convergencelabs.convergence.server.backend.services.domain.model.ot.xform.reference

import com.convergencelabs.convergence.server.backend.services.domain.model.ot.ObjectAddPropertyOperation
import com.convergencelabs.convergence.server.backend.services.domain.model.ot.xform.{IndexTransformer, ReferenceTransformationFunction}
import com.convergencelabs.convergence.server.model.domain.model.RangeReferenceValues
import com.convergencelabs.convergence.server.backend.services.domain.model.reference.RangeReference

object ObjectAddPropertyRangeTF extends ReferenceTransformationFunction[ObjectAddPropertyOperation, RangeReferenceValues] {
  def transform(op: ObjectAddPropertyOperation, values: RangeReferenceValues): Option[RangeReferenceValues] = {
    val xFormedRanges = values.values map { range =>
      val xFormed = IndexTransformer.handleInsert(List(range.to, range.from), 0, 0)
      RangeReference.Range(xFormed.head, xFormed.last)
    }
    Some(RangeReferenceValues(xFormedRanges))
  }
}
