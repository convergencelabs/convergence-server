package com.convergencelabs.server.domain.model.ot

import MoveDirection.Backward
import MoveDirection.Forward
import MoveDirection.Identity
import RangeIndexRelationship.After
import RangeIndexRelationship.Before
import RangeIndexRelationship.End
import RangeIndexRelationship.Start
import RangeIndexRelationship.Within

private[ot] object ArrayMoveReplaceTF extends OperationTransformationFunction[ArrayMoveOperation, ArrayReplaceOperation] {
  def transform(s: ArrayMoveOperation, c: ArrayReplaceOperation): (ArrayMoveOperation, ArrayReplaceOperation) = {
    ArrayMoveRangeHelper.getMoveDirection(s) match {
      case Forward => transformAgainstForwardMove(s, c)
      case Backward => transformAgainstBackwardMove(s, c)
      case Identity => transformAgainstIdentityMove(s, c)
    }
  }

  def transformAgainstForwardMove(s: ArrayMoveOperation, c: ArrayReplaceOperation): (ArrayMoveOperation, ArrayReplaceOperation) = {
    ArrayMoveRangeHelper.getRangeIndexRelationship(s, c.index) match {
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
    ArrayMoveRangeHelper.getRangeIndexRelationship(s, c.index) match {
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
