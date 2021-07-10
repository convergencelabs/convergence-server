/*
 * Copyright (c) 2021 - Convergence Labs, Inc.
 *
 * This file is part of the Convergence Server, which is released under
 * the terms of the GNU General Public License version 3 (GPLv3). A copy
 * of the GPLv3 should have been provided along with this file, typically
 * located in the "LICENSE" file, which is part of this source code package.
 * Alternatively, see <https://www.gnu.org/licenses/gpl-3.0.html> for the
 * full text of the GPLv3 license, if it was not provided.
 */

package com.convergencelabs.convergence.server.backend.services.domain.model.ot.xform.array

import com.convergencelabs.convergence.server.backend.services.domain.model.ot.xform.OperationPairExhaustiveSpec.ValueId
import com.convergencelabs.convergence.server.backend.services.domain.model.ot.xform.TransformationCase
import com.convergencelabs.convergence.server.backend.services.domain.model.ot.{ArrayReplaceOperation, DiscreteOperation}

class ArrayReplaceReplaceExhaustiveSpec extends ArrayOperationExhaustiveSpec[ArrayReplaceOperation, ArrayReplaceOperation]() {

  def generateCases(): List[TransformationCase[ArrayReplaceOperation, ArrayReplaceOperation]] = {
    val indices = generateIndices()
    val values = generateValues()

    for {
      i1 <- indices
      v1 <- values
      i2 <- indices
      v2 <- values
    } yield TransformationCase(
      ArrayReplaceOperation(ValueId, false, i1, v1),
      ArrayReplaceOperation(ValueId, false, i2, v2))
  }

  def transform(s: ArrayReplaceOperation, c: ArrayReplaceOperation): (DiscreteOperation, DiscreteOperation) = {
    ArrayReplaceReplaceTF.transform(s, c)
  }
}
