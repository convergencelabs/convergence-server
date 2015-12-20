package com.convergencelabs.server.domain.model.ot

private[ot] object ArrayRemoveMoveTF extends OperationTransformationFunction[ArrayRemoveOperation, ArrayMoveOperation] {
  def transform(s: ArrayRemoveOperation, c: ArrayMoveOperation): (ArrayRemoveOperation, ArrayMoveOperation) = {
    if (ArrayMoveRangeHelper.indexBeforeRange(c, s.index)) {
      (s, c.copy(fromIndex = c.fromIndex - 1, toIndex = c.toIndex - 1))
    } else if (ArrayMoveRangeHelper.indexAfterRange(c, s.index)) {
      (s, c)
    } else if (ArrayMoveRangeHelper.isForwardMove(c)) {
      transformAgainstForwardMove(s, c)
    } else if (ArrayMoveRangeHelper.isBackwardMoveMove(c)) {
      transformAgainstBackwardMove(s, c)
    } else {
      transformAgainstIdentityMove(s, c)
    }
  }

  def transformAgainstForwardMove(s: ArrayRemoveOperation, c: ArrayMoveOperation): (ArrayRemoveOperation, ArrayMoveOperation) = {
    // There are only three cases here.  Either the remove is at the start, the end, or in the middle.
    if (c.fromIndex == s.index) {
      // At the start.
      (s.copy(index = c.toIndex), c.copy(noOp = true))
    } else {
      // At the end or in the middle.
      (s.copy(index = s.index - 1), c.copy(toIndex = c.toIndex - 1))
    }
  }

  def transformAgainstBackwardMove(s: ArrayRemoveOperation, c: ArrayMoveOperation): (ArrayRemoveOperation, ArrayMoveOperation) = {
    // There are only three cases here.  Either the remove is at the start, the end, or in the middle.
    if (c.fromIndex == s.index) {
      // At the start.
      (s.copy(index = c.toIndex), c.copy(noOp = true))
    } else {
      // At the end or in the middle.
      (s.copy(index = s.index + 1), c.copy(fromIndex = c.fromIndex - 1))
    } 
  }

  def transformAgainstIdentityMove(s: ArrayRemoveOperation, c: ArrayMoveOperation): (ArrayRemoveOperation, ArrayMoveOperation) = {
    (s, c.copy(noOp = true))
  }
}
