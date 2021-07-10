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

import com.convergencelabs.convergence.server.backend.services.domain.model.ot.StringOperation
import com.convergencelabs.convergence.server.backend.services.domain.model.ot.xform.OperationPairExhaustiveSpec

import scala.reflect.ClassTag

object StringOperationExhaustiveSpec {
  val StringModelInitialState: String = "ABCDEFGH"
}

abstract class StringOperationExhaustiveSpec[S <: StringOperation, C <: StringOperation](implicit s: ClassTag[S], c: ClassTag[C])
    extends OperationPairExhaustiveSpec[MockStringModel, S, C] {

  def generateIndices(): List[Int] = {
    val len = StringOperationExhaustiveSpec.StringModelInitialState.length
    val indices = 0 until len
    indices.toList
  }

  def generateSpliceRanges(): List[StringSpliceRange] = {
    StringSpliceRangeGenerator.createDeleteRanges(StringOperationExhaustiveSpec.StringModelInitialState)
  }

  def createMockModel(): MockStringModel = {
    new MockStringModel(StringOperationExhaustiveSpec.StringModelInitialState)
  }
}
