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

class ObjectRemovePropertySetPropertyExhaustiveSpec extends ObjectOperationExhaustiveSpec[ObjectRemovePropertyOperation, ObjectSetPropertyOperation] {

  def generateCases(): List[TransformationCase[ObjectRemovePropertyOperation, ObjectSetPropertyOperation]] = {
    for {
      setProp <- ExistingProperties
      setValue <- NewValues
      removeProp <- ExistingProperties
    } yield TransformationCase(
      ObjectRemovePropertyOperation(ValueId, false, removeProp),
      ObjectSetPropertyOperation(ValueId, false, setProp, setValue))
  }

  def transform(s: ObjectRemovePropertyOperation, c: ObjectSetPropertyOperation): (DiscreteOperation, DiscreteOperation) = {
    ObjectRemovePropertySetPropertyTF.transform(s, c)
  }
}
