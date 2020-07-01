/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is part of the Convergence Server, which is released under
 * the terms of the GNU General Public License version 3 (GPLv3). A copy
 * of the GPLv3 should have been provided along with this file, typically
 * located in the "LICENSE" file, which is part of this source code package.
 * Alternatively, see <https://www.gnu.org/licenses/gpl-3.0.html> for the
 * full text of the GPLv3 license, if it was not provided.
 */

package com.convergencelabs.convergence.server.backend.services.domain.model.ot.xform.array

import com.convergencelabs.convergence.server.backend.services.domain.model.ot.RangeIndexRelationship.{After, Before, End, Start, Within}
import com.convergencelabs.convergence.server.backend.services.domain.model.ot._
import com.convergencelabs.convergence.server.backend.services.domain.model.ot.xform.OperationTransformationFunction
import com.convergencelabs.convergence.server.backend.services.domain.model.ot.xform.array.MoveDirection.{Backward, Forward, Identity}

private[ot] object ArrayMoveReplaceTF extends OperationTransformationFunction[ArrayMoveOperation, ArrayReplaceOperation] {
  def transform(s: ArrayMoveOperation, c: ArrayReplaceOperation): (ArrayMoveOperation, ArrayReplaceOperation) = {
    ArrayMoveHelper.getMoveDirection(s) match {
      case Forward => transformAgainstForwardMove(s, c)
      case Backward => transformAgainstBackwardMove(s, c)
      case Identity => transformAgainstIdentityMove(s, c)
    }
  }

  def transformAgainstForwardMove(s: ArrayMoveOperation, c: ArrayReplaceOperation): (ArrayMoveOperation, ArrayReplaceOperation) = {
    ArrayMoveHelper.getRangeIndexRelationship(s, c.index) match {
      case Before | After =>
        // A-MP-1 and A-MP-5
        (s, c)
      case Start =>
        // A-MP-2
        (s, c.copy(index = s.toIndex))
      case Within | End =>
        // A-MP-3 and A-MP-4
        (s, c.copy(index = c.index - 1))
    }
  }

  def transformAgainstBackwardMove(s: ArrayMoveOperation, c: ArrayReplaceOperation): (ArrayMoveOperation, ArrayReplaceOperation) = {
    ArrayMoveHelper.getRangeIndexRelationship(s, c.index) match {
      case Before | After =>
        // A-MP-6 and A-MP-10
        (s, c)
      case End =>
        // A-MP-7
        (s, c.copy(index = s.toIndex))
      case Within | Start =>
        // A-MP-8 and A-MP-9
        (s, c.copy(index = c.index + 1))
    }
  }

  private[this] def transformAgainstIdentityMove(s: ArrayMoveOperation, c: ArrayReplaceOperation): (ArrayMoveOperation, ArrayReplaceOperation) = {
    // A-MP-11, A-MP-12, A-MP-13
    (s, c)
  }
}
