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

package com.convergencelabs.convergence.server.backend.services.domain.model.ot.xform.string

import com.convergencelabs.convergence.server.backend.services.domain.model.ot._
import com.convergencelabs.convergence.server.backend.services.domain.model.ot.xform.OperationPairExhaustiveSpec.ValueId
import com.convergencelabs.convergence.server.backend.services.domain.model.ot.xform.TransformationCase

class StringSpliceSpliceExhaustiveSpec extends StringOperationExhaustiveSpec[StringSpliceOperation, StringSpliceOperation] {

  def generateCases(): List[TransformationCase[StringSpliceOperation, StringSpliceOperation]] = {
    val ranges = generateSpliceRanges()

    for {op1Range <- ranges
         op1InsertValue <- List("", "X")
         op2Range <- ranges
         op2InsertValue <- List("", "Y")} yield {
      TransformationCase(
        StringSpliceOperation(ValueId, noOp = false, op1Range.index, op1Range.length, op1InsertValue),
        StringSpliceOperation(ValueId, noOp = false, op2Range.index, op2Range.length, op2InsertValue))
    }
  }

  def transform(s: StringSpliceOperation, c: StringSpliceOperation): (DiscreteOperation, DiscreteOperation) = {
    StringSpliceSpliceTF.transform(s, c)
  }
}
