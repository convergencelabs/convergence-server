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

import com.convergencelabs.convergence.server.domain.model.ot.xform.OperationTransformationFunction
import com.convergencelabs.convergence.server.domain.model.ot._

import MoveDirection.Backward
import MoveDirection.Forward
import MoveDirection.Identity
import RangeIndexRelationship.After
import RangeIndexRelationship.Before
import RangeIndexRelationship.End
import RangeIndexRelationship.Start
import RangeIndexRelationship.Within

private[ot] object ArrayMoveRemoveTF extends OperationTransformationFunction[ArrayMoveOperation, ArrayRemoveOperation] {
  def transform(s: ArrayMoveOperation, c: ArrayRemoveOperation): (ArrayMoveOperation, ArrayRemoveOperation) = {
    ArrayMoveHelper.getMoveDirection(s) match {
      case Forward => transformAgainstForwardMove(s, c)
      case Backward => transformAgainstBackwardMove(s, c)
      case Identity => transformAgainstIdentityMove(s, c)
    }
  }

  def transformAgainstForwardMove(s: ArrayMoveOperation, c: ArrayRemoveOperation): (ArrayMoveOperation, ArrayRemoveOperation) = {
    ArrayMoveHelper.getRangeIndexRelationship(s, c.index) match {
      case Before =>
        // A-MR-1
        (s.copy(fromIndex = s.fromIndex - 1, toIndex = s.toIndex - 1), c)
      case Start =>
        // A-MR-2
        (s.copy(noOp = true), c.copy(index = s.toIndex))
      case Within | End =>
        // A-MR-3 and A-MR-4
        (s.copy(toIndex = s.toIndex - 1), c.copy(index = c.index - 1))
      case After =>
        // A-MR-5
        (s, c)
    }
  }

  def transformAgainstBackwardMove(s: ArrayMoveOperation, c: ArrayRemoveOperation): (ArrayMoveOperation, ArrayRemoveOperation) = {
    ArrayMoveHelper.getRangeIndexRelationship(s, c.index) match {
      case Before =>
        // A-MR-6
        (s.copy(fromIndex = s.fromIndex - 1, toIndex = s.toIndex - 1), c)
      case Start | Within =>
        // A-MR-7 and A-MR-8
        (s.copy(fromIndex = s.fromIndex - 1), c.copy(index = c.index + 1))
      case End =>
        // A-MR-9
        (s.copy(noOp = true), c.copy(index = s.toIndex))
      case After =>
        // A-MR-10
        (s, c)
    }
  }

  private[this] def transformAgainstIdentityMove(s: ArrayMoveOperation, c: ArrayRemoveOperation): (ArrayMoveOperation, ArrayRemoveOperation) = {
    ArrayMoveHelper.getRangeIndexRelationship(s, c.index) match {
      case Before =>
        // A-MR-11
        (s.copy(fromIndex = s.fromIndex - 1, toIndex = s.toIndex - 1), c)
      case Start | Within | End =>
        // A-MR-12
        (s.copy(noOp = true), c)
      case After =>
        // A-MR-13
        (s, c)
    }
  }
}
