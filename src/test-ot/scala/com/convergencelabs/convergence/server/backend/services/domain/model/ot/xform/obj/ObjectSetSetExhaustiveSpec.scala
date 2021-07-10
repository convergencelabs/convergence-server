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

import com.convergencelabs.convergence.server.backend.services.domain.model.ot._
import com.convergencelabs.convergence.server.backend.services.domain.model.ot.xform.OperationPairExhaustiveSpec.ValueId
import com.convergencelabs.convergence.server.backend.services.domain.model.ot.xform.TransformationCase
import com.convergencelabs.convergence.server.backend.services.domain.model.ot.xform.obj.ObjectOperationExhaustiveSpec._

class ObjectSetSetExhaustiveSpec extends ObjectOperationExhaustiveSpec[ObjectSetOperation, ObjectSetOperation] {

  def generateCases(): List[TransformationCase[ObjectSetOperation, ObjectSetOperation]] = {
    for {
      newObject1 <- SetObjects
      newObject2 <- SetObjects
    } yield TransformationCase(
      ObjectSetOperation(ValueId, false, newObject1),
      ObjectSetOperation(ValueId, false, newObject2))
  }

  def transform(s: ObjectSetOperation, c: ObjectSetOperation): (DiscreteOperation, DiscreteOperation) = {
    ObjectSetSetTF.transform(s, c)
  }
}
