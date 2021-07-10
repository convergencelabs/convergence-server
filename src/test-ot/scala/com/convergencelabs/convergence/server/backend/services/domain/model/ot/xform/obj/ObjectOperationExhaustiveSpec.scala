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

package com.convergencelabs.convergence.server.backend.services.domain.model.ot.xform.obj

import com.convergencelabs.convergence.server.backend.services.domain.model.ot.ObjectOperation
import com.convergencelabs.convergence.server.backend.services.domain.model.ot.xform.OperationPairExhaustiveSpec
import com.convergencelabs.convergence.server.model.domain.model.DoubleValue
import org.json4s.JObject
import org.json4s.JsonAST.JInt

import scala.math.BigInt.int2bigInt
import scala.reflect.ClassTag

// scalastyle:off magic.number
object ObjectOperationExhaustiveSpec {
  val InitialState: JObject = JObject(List(("A" -> JInt(1))))

  val SetObject1 = Map("X" -> DoubleValue("1", 24))
  val SetObject2 = Map("Y" -> DoubleValue("1", 25))

  val SetObjects = List(SetObject1, SetObject2)

  val ExistingProperties = List("A", "B", "C")
  val NewProperties = List("D", "E", "F")
  val NewValues = List(DoubleValue("vid2", 4), DoubleValue("vid3", 5), DoubleValue("vid4", 6))
}

abstract class ObjectOperationExhaustiveSpec[S <: ObjectOperation, C <: ObjectOperation](implicit s: ClassTag[S], c: ClassTag[C])
    extends OperationPairExhaustiveSpec[MockObjectModel, S, C] {
  import ObjectOperationExhaustiveSpec._

  def createMockModel(): MockObjectModel = {
    new MockObjectModel(InitialState.values)
  }
}
