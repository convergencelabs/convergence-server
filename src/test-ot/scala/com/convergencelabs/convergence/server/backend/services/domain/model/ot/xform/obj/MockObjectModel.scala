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
import com.convergencelabs.convergence.server.backend.services.domain.model.ot.xform.MockModel

class MockObjectModel(private var state: Map[String, Any]) extends MockModel {

  def updateModel(op: DiscreteOperation): Unit = {
    op match {
      case addProp: ObjectAddPropertyOperation => handleAddProperty(addProp)
      case setProp: ObjectSetPropertyOperation => handleSetPropery(setProp)
      case removeProp: ObjectRemovePropertyOperation => handleRemoveProperty(removeProp)
      case set: ObjectSetOperation => handleSet(set)
      case x: Any =>
        throw new IllegalArgumentException()
    }
  }

  private def handleAddProperty(op: ObjectAddPropertyOperation): Unit = {
    state = state + (op.property -> op.value)
  }

  private def handleSetPropery(op: ObjectSetPropertyOperation): Unit = {
    state = state + (op.property -> op.value)
  }

  private def handleRemoveProperty(op: ObjectRemovePropertyOperation): Unit = {
    state = state - op.property
  }

  private def handleSet(op: ObjectSetOperation): Unit = {
    state = op.value
  }

  def getData(): Map[String, Any] = {
    state
  }
}
