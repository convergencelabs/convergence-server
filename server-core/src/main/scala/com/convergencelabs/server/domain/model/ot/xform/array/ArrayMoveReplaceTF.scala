package com.convergencelabs.server.domain.model.ot

private[ot] object ArrayMoveReplaceTF extends OperationTransformationFunction[ArrayMoveOperation, ArrayReplaceOperation] {
  def transform(s: ArrayMoveOperation, c: ArrayReplaceOperation): (ArrayMoveOperation, ArrayReplaceOperation) = {
    if (ArrayMoveRangeHelper.indexBeforeRange(s, c.index)
      || ArrayMoveRangeHelper.indexAfterRange(s, c.index)) {
      (s, c)
    } else if (ArrayMoveRangeHelper.isForwardMove(s)) {
      transformAgainstForwardMove(s, c)
    } else if (ArrayMoveRangeHelper.isBackwardMoveMove(s)) {
      transformAgainstBackwardMove(s, c)
    } else {
      // Identity Move
      (s, c)
    }
  }

  def transformAgainstForwardMove(s: ArrayMoveOperation, c: ArrayReplaceOperation): (ArrayMoveOperation, ArrayReplaceOperation) = {
    // There are only three cases.  The replace is at the start, the end, or in the middle.
    if (s.fromIndex == c.index) {
      // at the start.
      (s, c.copy(index = s.toIndex))
    } else {
      // At the end or middle.
      (s, c.copy(index = c.index - 1))
    } 
  }

  def transformAgainstBackwardMove(s: ArrayMoveOperation, c: ArrayReplaceOperation): (ArrayMoveOperation, ArrayReplaceOperation) = {
    // There are only three cases.  The replace is at the start, the end, or in the middle.
    if (s.fromIndex == c.index) {
      // at the start.
      (s, c.copy(index = s.toIndex))
    } else {
      // At the end or middle.
      (s, c.copy(index = c.index + 1))
    } 
  }
}
