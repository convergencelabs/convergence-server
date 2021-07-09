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
import com.convergencelabs.convergence.server.model.domain.model.IndexReferenceValues

object ObjectAddPropertyIndexTF extends ReferenceTransformationFunction[ObjectAddPropertyOperation, IndexReferenceValues] {
  def transform(op: ObjectAddPropertyOperation, values: IndexReferenceValues): Option[IndexReferenceValues] = {
    val xFormed = IndexTransformer.handleInsert(values.values, 0, 0)
    Some(IndexReferenceValues(xFormed))
  }
}
