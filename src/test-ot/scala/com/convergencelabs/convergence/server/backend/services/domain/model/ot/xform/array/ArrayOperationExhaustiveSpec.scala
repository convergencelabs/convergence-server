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

import com.convergencelabs.convergence.server.backend.services.domain.model.ot.ArrayOperation
import com.convergencelabs.convergence.server.backend.services.domain.model.ot.xform.OperationPairExhaustiveSpec
import com.convergencelabs.convergence.server.model.domain.model.{DataValue, StringValue}

import scala.reflect.ClassTag

object ArrayOperationExhaustiveSpec {
  val ArrayLength: Int = 15

  val Value1: StringValue = StringValue("vid1", "value1")
  val Value2: StringValue = StringValue("vid2", "value2")
  val ArrayValue = List(StringValue("vid2", "X"))
}

abstract class ArrayOperationExhaustiveSpec[S <: ArrayOperation, C <: ArrayOperation](implicit s: ClassTag[S], c: ClassTag[C])
    extends OperationPairExhaustiveSpec[MockArrayModel, S, C]() {

  import ArrayOperationExhaustiveSpec._

  def generateIndices(): List[Int] = {
    (0 until ArrayLength).toList
  }

  def generateValues(): List[DataValue] = {
    List(Value1, Value2)
  }

  def generateMoveRanges(): List[ArrayMoveRange] = {
    MoveRangeGenerator.createRanges(ArrayLength)
  }

  def createMockModel(): MockArrayModel = {
    new MockArrayModel((0 to ArrayLength).toList)
  }
}
