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
import com.convergencelabs.convergence.server.backend.services.domain.model.ot.{ArrayRemoveOperation, DiscreteOperation}

class ArrayRemoveRemoveExhaustiveSpec extends ArrayOperationExhaustiveSpec[ArrayRemoveOperation, ArrayRemoveOperation] {

  def generateCases(): List[TransformationCase[ArrayRemoveOperation, ArrayRemoveOperation]] = {
    val indices = generateIndices()
    for { i1 <- indices; i2 <- indices } yield TransformationCase(
      ArrayRemoveOperation(ValueId, false, i1),
      ArrayRemoveOperation(ValueId, false, i2))
  }

  def transform(s: ArrayRemoveOperation, c: ArrayRemoveOperation): (DiscreteOperation, DiscreteOperation) = {
    ArrayRemoveRemoveTF.transform(s, c)
  }
}
