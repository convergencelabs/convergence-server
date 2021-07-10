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

import com.convergencelabs.convergence.server.backend.services.domain.model.ot._
import com.convergencelabs.convergence.server.backend.services.domain.model.ot.xform.MockModel

class MockArrayModel(private var state: List[Any]) extends MockModel {

  def updateModel(op: DiscreteOperation): Unit = {
    op match {
      case insert: ArrayInsertOperation => handleInsert(insert)
      case remove: ArrayRemoveOperation => handleRemove(remove)
      case replace: ArrayReplaceOperation => handleReplace(replace)
      case move: ArrayMoveOperation => handleMove(move)
      case set: ArraySetOperation => handleSet(set)
      case invalid: Any =>
        throw new IllegalArgumentException("Invalid operation type: " + invalid)
    }
  }

  private def handleInsert(op: ArrayInsertOperation): Unit = {
    state = state.patch(op.index, Seq(op.value), 0)
  }

  private def handleRemove(op: ArrayRemoveOperation): Unit = {
    state = state.patch(op.index, Nil, 1)
  }

  private def handleReplace(op: ArrayReplaceOperation): Unit = {
    state = state.patch(op.index, Seq(op.value), 1)
  }

  private def handleMove(op: ArrayMoveOperation): Unit = {
    val element = state(op.fromIndex)
    state = state.patch(op.fromIndex, Nil, 1)
    state = state.patch(op.toIndex, Seq(element), 0)
  }

  private def handleSet(op: ArraySetOperation): Unit = {
    state = op.value
  }

  def getData(): List[Any] = {
    state
  }
}
