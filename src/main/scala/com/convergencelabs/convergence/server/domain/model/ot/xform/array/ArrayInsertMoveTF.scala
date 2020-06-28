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

package com.convergencelabs.convergence.server.domain.model.ot.xform.array

import com.convergencelabs.convergence.server.domain.model.ot.RangeIndexRelationship.{After, Before, End, Start, Within}
import com.convergencelabs.convergence.server.domain.model.ot._
import com.convergencelabs.convergence.server.domain.model.ot.xform.OperationTransformationFunction
import com.convergencelabs.convergence.server.domain.model.ot.xform.array.MoveDirection.{Backward, Forward, Identity}

/**
 * This transformation function handles a concurrent server
 * ArrayInsertOperation and a client ArrayMoveOperation.
 */
private[ot] object ArrayInsertMoveTF extends OperationTransformationFunction[ArrayInsertOperation, ArrayMoveOperation] {
  def transform(s: ArrayInsertOperation, c: ArrayMoveOperation): (ArrayInsertOperation, ArrayMoveOperation) = {
    ArrayMoveHelper.getMoveDirection(c) match {
      case Forward => transformAgainstForwardMove(s, c)
      case Backward => transformAgainstBackwardMove(s, c)
      case Identity => transformAgainstIdentityMove(s, c)
    }
  }

  private[this] def transformAgainstForwardMove(s: ArrayInsertOperation, c: ArrayMoveOperation): (ArrayInsertOperation, ArrayMoveOperation) = {
    ArrayMoveHelper.getRangeIndexRelationship(c, s.index) match {
      case Before | Start =>
        // A-IM-1 and A-IM-2
        (s, c.copy(fromIndex = c.fromIndex + 1, toIndex = c.toIndex + 1))
      case Within | End =>
        // A-IM-3 and A-IM-4
        (s.copy(index = s.index - 1), c.copy(toIndex = c.toIndex + 1))
      case After =>
        // A-IM-5
        (s, c)
    }
  }

  private[this] def transformAgainstBackwardMove(s: ArrayInsertOperation, c: ArrayMoveOperation): (ArrayInsertOperation, ArrayMoveOperation) = {
    ArrayMoveHelper.getRangeIndexRelationship(c, s.index) match {
      case Before | Start =>
        // A-IM-6 and A-IM-7
        (s, c.copy(fromIndex = c.fromIndex + 1, toIndex = c.toIndex + 1))
      case Within | End =>
        // A-IM-8 and A-IM-9
        (s.copy(index = s.index + 1), c.copy(fromIndex = c.fromIndex + 1))
      case After =>
        // A-IM-10
        (s, c)
    }
  }

  private[this] def transformAgainstIdentityMove(s: ArrayInsertOperation, c: ArrayMoveOperation): (ArrayInsertOperation, ArrayMoveOperation) = {
    ArrayMoveHelper.getRangeIndexRelationship(c, s.index) match {
      case After =>
        // A-IM-13
        (s, c)
      case _ =>
        // A-IM-11 and A-IM-12
        (s, c.copy(fromIndex = c.fromIndex + 1, toIndex = c.toIndex + 1))
    }
  }
}
