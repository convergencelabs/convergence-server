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

import com.convergencelabs.convergence.server.backend.services.domain.model.ot.xform.{OperationPairExhaustiveSpec, TransformationCase}
import com.convergencelabs.convergence.server.backend.services.domain.model.ot.{ArrayMoveOperation, ArraySetOperation, DiscreteOperation}

class ArrayMoveSetExhaustiveSpec extends ArrayOperationExhaustiveSpec[ArrayMoveOperation, ArraySetOperation] {

  def generateCases(): List[TransformationCase[ArrayMoveOperation, ArraySetOperation]] = {
    for { r <- generateMoveRanges() } yield TransformationCase(
      ArrayMoveOperation(OperationPairExhaustiveSpec.ValueId, noOp = false, r.fromIndex, r.toIndex),
      ArraySetOperation(OperationPairExhaustiveSpec.ValueId, noOp = false, ArrayOperationExhaustiveSpec.ArrayValue))
  }

  def transform(s: ArrayMoveOperation, c: ArraySetOperation): (DiscreteOperation, DiscreteOperation) = {
    ArrayMoveSetTF.transform(s, c)
  }
}
