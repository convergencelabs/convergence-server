package com.convergencelabs.server.domain.model.ot

private[ot] object ArrayMoveRemoveTF extends OperationTransformationFunction[ArrayMoveOperation, ArrayRemoveOperation] {
  def transform(s: ArrayMoveOperation, c: ArrayRemoveOperation): (ArrayMoveOperation, ArrayRemoveOperation) = {
    if (ArrayMoveRangeHelper.isIdentityMove(s)) {
      (s, c)
    } else if (ArrayMoveRangeHelper.indexBeforeRange(s, c.index)) {
      (s.copy(fromIndex = s.fromIndex - 1, toIndex = s.toIndex - 1), c)
    } else if (ArrayMoveRangeHelper.indexAfterRange(s, c.index)) {
      (s, c)
    } else if (ArrayMoveRangeHelper.isForwardMove(s)) {
      transformAgainstForwardMove(s, c)
    } else if (ArrayMoveRangeHelper.isBackwardMoveMove(s)) {
      transformAgainstBackwardMove(s, c)
    } else {
      ??? // FIXME real exception
    }
  }

  def transformAgainstForwardMove(s: ArrayMoveOperation, c: ArrayRemoveOperation): (ArrayMoveOperation, ArrayRemoveOperation) = {
    if (s.fromIndex == c.index) {
      (s.copy(noOp = true), c.copy(index = s.toIndex))
    } else if (ArrayMoveRangeHelper.indexWithinRange(s, c.index) || s.toIndex == c.index) {
      (s.copy(fromIndex = s.fromIndex - 1), c.copy(index = c.index - 1))
    } else {
      (s, c) // FIXME does this happen?
    }
  }

  def transformAgainstBackwardMove(s: ArrayMoveOperation, c: ArrayRemoveOperation): (ArrayMoveOperation, ArrayRemoveOperation) = {
    if (s.fromIndex == c.index) {
      (s.copy(noOp = true), c.copy(index = s.toIndex))
    } else if (ArrayMoveRangeHelper.indexWithinRange(s, c.index) || s.toIndex == c.index) {
      (s.copy(fromIndex = s.fromIndex - 1), c.copy(index = c.index + 1))
    } else {
      (s, c) // FIXME does this happen?
    }
  }
}