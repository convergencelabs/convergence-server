package com.convergencelabs.server.domain.model.ot

import MoveDirection._
import RangeIndexRelationship._

private[ot] object ArrayInsertMoveTF extends OperationTransformationFunction[ArrayInsertOperation, ArrayMoveOperation] {
  def transform(s: ArrayInsertOperation, c: ArrayMoveOperation): (ArrayInsertOperation, ArrayMoveOperation) = {
    ArrayMoveRangeHelper.getMoveDirection(c) match {
      case Forward => transformAgainstForwardMove(s, c)
      case Backward => transformAgainstBackwardMove(s, c)
      case Identity => transformAgainstIdentityMove(s, c)
    }
  }

  def transformAgainstForwardMove(s: ArrayInsertOperation, c: ArrayMoveOperation): (ArrayInsertOperation, ArrayMoveOperation) = {
    ArrayMoveRangeHelper.getRangeIndexRelationship(c, s.index) match {
      case Before | Start =>
        (s, c.copy(fromIndex = c.fromIndex + 1, toIndex = c.toIndex + 1))
      case Within | End =>
        (s.copy(index = s.index - 1), c.copy(toIndex = c.toIndex + 1))
      case After => (s, c)
    }
  }

  def transformAgainstBackwardMove(s: ArrayInsertOperation, c: ArrayMoveOperation): (ArrayInsertOperation, ArrayMoveOperation) = {
    ArrayMoveRangeHelper.getRangeIndexRelationship(c, s.index) match {
      case Before | Start =>
        (s, c.copy(fromIndex = c.fromIndex + 1, toIndex = c.toIndex + 1))
      case Within | End =>
        (s.copy(index = s.index + 1), c.copy(fromIndex = c.fromIndex + 1))
      case After => (s, c)
    }
  }

  private[this] def transformAgainstIdentityMove(s: ArrayInsertOperation, c: ArrayMoveOperation): (ArrayInsertOperation, ArrayMoveOperation) = {
    ArrayMoveRangeHelper.getRangeIndexRelationship(c, s.index) match {
      case After => (s, c)
      case _ =>
        (s, c.copy(fromIndex = c.fromIndex + 1, toIndex = c.toIndex + 1))
    }
  }
}
