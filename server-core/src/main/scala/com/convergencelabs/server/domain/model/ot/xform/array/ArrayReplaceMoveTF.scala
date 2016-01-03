package com.convergencelabs.server.domain.model.ot

private[ot] object ArrayReplaceMoveTF extends OperationTransformationFunction[ArrayReplaceOperation, ArrayMoveOperation] {
  def transform(s: ArrayReplaceOperation, c: ArrayMoveOperation): (ArrayReplaceOperation, ArrayMoveOperation) = {
    if (ArrayMoveRangeHelper.indexBeforeRange(c, s.index)
      || ArrayMoveRangeHelper.indexAfterRange(c, s.index)) {
      (s, c)
    } else if (ArrayMoveRangeHelper.isForwardMove(c)) {
      transformAgainstForwardMove(s, c)
    } else if (ArrayMoveRangeHelper.isBackwardMoveMove(c)) {
      transformAgainstBackwardMove(s, c)
    } else {
      // Identity Move
      (s, c)
    }
  }

  def transformAgainstForwardMove(s: ArrayReplaceOperation, c: ArrayMoveOperation): (ArrayReplaceOperation, ArrayMoveOperation) = {
    // There are only three cases.  The replace is at the start, the end, or in the middle.
    if (c.fromIndex == s.index) {
      // at the start.
      (s.copy(index = c.toIndex), c)
    } else {
      // At the end or middle.
      (s.copy(index = s.index - 1), c)
    }
  }

  def transformAgainstBackwardMove(s: ArrayReplaceOperation, c: ArrayMoveOperation): (ArrayReplaceOperation, ArrayMoveOperation) = {
    if (c.fromIndex == s.index) {
      // at the start.
      (s.copy(index = c.toIndex), c)
    } else {
      // At the end or middle.
      (s.copy(index = s.index + 1), c)
    }
  }
}
