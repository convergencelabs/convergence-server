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
import com.convergencelabs.convergence.server.backend.services.domain.model.ot.{ArrayMoveOperation, DiscreteOperation}

class ArrayMoveMoveExhaustiveSpec extends ArrayOperationExhaustiveSpec[ArrayMoveOperation, ArrayMoveOperation] {

  def generateCases(): List[TransformationCase[ArrayMoveOperation, ArrayMoveOperation]] = {
    val ranges = generateMoveRanges()
    for { r1 <- ranges; r2 <- ranges } yield TransformationCase(
      ArrayMoveOperation(ValueId, noOp = false, r1.fromIndex, r1.toIndex),
      ArrayMoveOperation(ValueId, noOp = false, r2.fromIndex, r2.toIndex))
  }

  def transform(s: ArrayMoveOperation, c: ArrayMoveOperation): (DiscreteOperation, DiscreteOperation) = {
    ArrayMoveMoveTF.transform(s, c)
  }
}
