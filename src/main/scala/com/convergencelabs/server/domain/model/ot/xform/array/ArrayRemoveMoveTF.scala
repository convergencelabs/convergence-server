/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

package com.convergencelabs.server.domain.model.ot

import MoveDirection.Backward
import MoveDirection.Forward
import MoveDirection.Identity
import RangeIndexRelationship.After
import RangeIndexRelationship.Before
import RangeIndexRelationship.End
import RangeIndexRelationship.Start
import RangeIndexRelationship.Within

private[ot] object ArrayRemoveMoveTF extends OperationTransformationFunction[ArrayRemoveOperation, ArrayMoveOperation] {
  def transform(s: ArrayRemoveOperation, c: ArrayMoveOperation): (ArrayRemoveOperation, ArrayMoveOperation) = {
    ArrayMoveHelper.getMoveDirection(c) match {
      case Forward => transformAgainstForwardMove(s, c)
      case Backward => transformAgainstBackwardMove(s, c)
      case Identity => transformAgainstIdentityMove(s, c)
    }
  }

  def transformAgainstForwardMove(s: ArrayRemoveOperation, c: ArrayMoveOperation): (ArrayRemoveOperation, ArrayMoveOperation) = {
    ArrayMoveHelper.getRangeIndexRelationship(c, s.index) match {
      case Before =>
        // A-RM-1
        (s, c.copy(fromIndex = c.fromIndex - 1, toIndex = c.toIndex - 1))
      case Start =>
        // A-RM-2
        (s.copy(index = c.toIndex), c.copy(noOp = true))
      case Within | End =>
        // A-RM-3 and A-RM-4
        (s.copy(index = s.index - 1), c.copy(toIndex = c.toIndex - 1))
      case After =>
        // A-RM-5
        (s, c)
    }
  }

  def transformAgainstBackwardMove(s: ArrayRemoveOperation, c: ArrayMoveOperation): (ArrayRemoveOperation, ArrayMoveOperation) = {
    ArrayMoveHelper.getRangeIndexRelationship(c, s.index) match {
      case Before =>
        // A-RM-6
        (s, c.copy(fromIndex = c.fromIndex - 1, toIndex = c.toIndex - 1))
      case Start | Within =>
        // A-RM-7 and A-RM-8
        (s.copy(index = s.index + 1), c.copy(fromIndex = c.fromIndex - 1))
      case End =>
        // A-RM-9
        (s.copy(index = c.toIndex), c.copy(noOp = true))
      case After =>
        // A-RM-10
        (s, c)
    }
  }

  private[this] def transformAgainstIdentityMove(s: ArrayRemoveOperation, c: ArrayMoveOperation): (ArrayRemoveOperation, ArrayMoveOperation) = {
    ArrayMoveHelper.getRangeIndexRelationship(c, s.index) match {
      case Before =>
        // A-RM-11
        (s, c.copy(fromIndex = c.fromIndex - 1, toIndex = c.toIndex - 1))
      case Start | Within | End =>
        // A-MR-12
        (s, c.copy(noOp = true))
      case After =>
        // A-MR-13
        (s, c)
    }
  }
}
