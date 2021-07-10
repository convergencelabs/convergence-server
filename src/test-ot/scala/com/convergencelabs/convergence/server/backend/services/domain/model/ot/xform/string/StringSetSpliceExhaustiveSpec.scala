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

class StringSetSpliceExhaustiveSpec extends StringOperationExhaustiveSpec[StringSetOperation, StringSpliceOperation] {

  def generateCases(): List[TransformationCase[StringSetOperation, StringSpliceOperation]] = {
    for {
      s <- List("", "X", "LargerString")
      r <- generateSpliceRanges()
      i <- List("", "X")
    } yield TransformationCase(
      StringSetOperation(ValueId, noOp = false, s),
      StringSpliceOperation(ValueId, noOp = false, r.index, r.length, i))
  }

  def transform(s: StringSetOperation, c: StringSpliceOperation): (DiscreteOperation, DiscreteOperation) = {
    StringSetSpliceTF.transform(s, c)
  }
}
