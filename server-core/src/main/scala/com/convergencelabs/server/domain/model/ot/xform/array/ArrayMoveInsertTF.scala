package com.convergencelabs.server.domain.model.ot

import MoveDirection.Backward
import MoveDirection.Forward
import MoveDirection.Identity
import RangeIndexRelationship.After
import RangeIndexRelationship.Before
import RangeIndexRelationship.End
import RangeIndexRelationship.Start
import RangeIndexRelationship.Within

private[ot] object ArrayMoveInsertTF extends OperationTransformationFunction[ArrayMoveOperation, ArrayInsertOperation] {

  def transform(s: ArrayMoveOperation, c: ArrayInsertOperation): (ArrayMoveOperation, ArrayInsertOperation) = {
    ArrayMoveRangeHelper.getMoveDirection(s) match {
      case Forward => transformAgainstForwardMove(s, c)
      case Backward => transformAgainstBackwardMove(s, c)
      case Identity => transformAgainstIdentityMove(s, c)
    }
  }

  private[this] def transformAgainstForwardMove(s: ArrayMoveOperation, c: ArrayInsertOperation): (ArrayMoveOperation, ArrayInsertOperation) = {
    ArrayMoveRangeHelper.getRangeIndexRelationship(s, c.index) match {
      case Before | Start =>
        // A-MI-1 and A-MI-2
        (s.copy(fromIndex = s.fromIndex + 1, toIndex = s.toIndex + 1), c)
      case Within | End =>
        // A-MI-3 and A-MI-4
        (s.copy(toIndex = s.toIndex + 1), c.copy(index = c.index - 1))
      case After =>
        // A-MI-5
        (s, c)
    }
  }

  private[this] def transformAgainstBackwardMove(s: ArrayMoveOperation, c: ArrayInsertOperation): (ArrayMoveOperation, ArrayInsertOperation) = {
    ArrayMoveRangeHelper.getRangeIndexRelationship(s, c.index) match {
      case Before | Start =>
        // A-MI-6 and A-MI-7
        (s.copy(fromIndex = s.fromIndex + 1, toIndex = s.toIndex + 1), c)
      case Within | End =>
        // A-MI-8 and A-MI-9
        (s.copy(fromIndex = s.fromIndex + 1), c.copy(index = c.index + 1))
      case After =>
        // A-MI-10
        (s, c)
    }
  }

  private[this] def transformAgainstIdentityMove(s: ArrayMoveOperation, c: ArrayInsertOperation): (ArrayMoveOperation, ArrayInsertOperation) = {
    ArrayMoveRangeHelper.getRangeIndexRelationship(s, c.index) match {
      case After =>
        // A-MI-13
        (s, c)
      case _ =>
        // A-MI-11 and A-MI-12
        (s.copy(fromIndex = s.fromIndex + 1, toIndex = s.toIndex + 1), c)
    }
  }
}
