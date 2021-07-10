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
import com.convergencelabs.convergence.server.backend.services.domain.model.ot.{ArrayInsertOperation, ArrayMoveOperation, DiscreteOperation}

class ArrayMoveInsertExhaustiveSpec extends ArrayOperationExhaustiveSpec[ArrayMoveOperation, ArrayInsertOperation] {

  def generateCases(): List[TransformationCase[ArrayMoveOperation, ArrayInsertOperation]] = {
    for { r <- generateMoveRanges(); i <- generateIndices() } yield TransformationCase(
      ArrayMoveOperation(OperationPairExhaustiveSpec.ValueId, noOp = false, r.fromIndex, r.toIndex),
      ArrayInsertOperation(OperationPairExhaustiveSpec.ValueId, noOp = false, i, ArrayOperationExhaustiveSpec.Value1))
  }

  def transform(s: ArrayMoveOperation, c: ArrayInsertOperation): (DiscreteOperation, DiscreteOperation) = {
    ArrayMoveInsertTF.transform(s, c)
  }
}
