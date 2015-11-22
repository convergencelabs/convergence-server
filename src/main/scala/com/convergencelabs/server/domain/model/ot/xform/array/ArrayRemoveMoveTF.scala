package com.convergencelabs.server.domain.model.ot

private[ot] object ArrayRemoveMoveTF extends OperationTransformationFunction[ArrayRemoveOperation, ArrayMoveOperation] {
  def transform(s: ArrayRemoveOperation, c: ArrayMoveOperation): (ArrayRemoveOperation, ArrayMoveOperation) = {
    if (ArrayMoveRangeHelper.isIdentityMove(c)) {
      (s, c)
    } else if (ArrayMoveRangeHelper.indexBeforeRange(c, s.index)) {
      (s, c.copy(fromIndex = c.fromIndex - 1, toIndex = c.toIndex - 1))
    } else if (ArrayMoveRangeHelper.indexAfterRange(c, s.index)) {
      (s, c)
    } else if (ArrayMoveRangeHelper.isForwardMove(c)) {
      transformAgainstForwardMove(s, c)
    } else if (ArrayMoveRangeHelper.isBackwardMoveMove(c)) {
      transformAgainstBackwardMove(s, c)
    } else {
      ??? // Unanticipated case
    }
  }

  def transformAgainstForwardMove(s: ArrayRemoveOperation, c: ArrayMoveOperation): (ArrayRemoveOperation, ArrayMoveOperation) = {
    if (c.fromIndex == s.index) {
      (s.copy(index = c.toIndex), c.copy(noOp = true))
    } else if (ArrayMoveRangeHelper.indexWithinRange(c, s.index) || c.toIndex == s.index) {
      (s.copy(index = s.index - 1), c.copy(toIndex = c.toIndex - 1))
    } else {
      (s, c)
    }
  }

  def transformAgainstBackwardMove(s: ArrayRemoveOperation, c: ArrayMoveOperation): (ArrayRemoveOperation, ArrayMoveOperation) = {
    if (c.fromIndex == s.index) {
      (s.copy(index = c.toIndex), c.copy(noOp = true))
    } else if (ArrayMoveRangeHelper.indexWithinRange(c, s.index) || c.toIndex == s.index) {
      (s.copy(index = s.index + 1), c.copy(fromIndex = c.fromIndex - 1))
    } else {
      (s, c)
    }
  }
}