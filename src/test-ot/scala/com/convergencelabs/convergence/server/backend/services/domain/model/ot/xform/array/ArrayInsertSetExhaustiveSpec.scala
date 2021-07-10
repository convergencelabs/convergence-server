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
import com.convergencelabs.convergence.server.backend.services.domain.model.ot.{ArrayInsertOperation, ArraySetOperation, DiscreteOperation}

class ArrayInsertSetExhaustiveSpec extends ArrayOperationExhaustiveSpec[ArrayInsertOperation, ArraySetOperation] {

  def generateCases(): List[TransformationCase[ArrayInsertOperation, ArraySetOperation]] = {
    for { i1 <- generateIndices() } yield TransformationCase(
      ArrayInsertOperation(OperationPairExhaustiveSpec.ValueId, noOp = false, i1, ArrayOperationExhaustiveSpec.Value1),
      ArraySetOperation(OperationPairExhaustiveSpec.ValueId, noOp = false, ArrayOperationExhaustiveSpec.ArrayValue))
  }

  def transform(s: ArrayInsertOperation, c: ArraySetOperation): (DiscreteOperation, DiscreteOperation) = {
    ArrayInsertSetTF.transform(s, c)
  }

}
