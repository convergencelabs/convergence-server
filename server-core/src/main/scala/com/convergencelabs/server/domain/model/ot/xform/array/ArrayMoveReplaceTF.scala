package com.convergencelabs.server.domain.model.ot

private[ot] object ArrayMoveReplaceTF extends OperationTransformationFunction[ArrayMoveOperation, ArrayReplaceOperation] {
  def transform(s: ArrayMoveOperation, c: ArrayReplaceOperation): (ArrayMoveOperation, ArrayReplaceOperation) = {
    if (ArrayMoveRangeHelper.isIdentityMove(s)) {
      (s, c)
    } else if (ArrayMoveRangeHelper.indexBeforeRange(s, c.index)
      || ArrayMoveRangeHelper.indexAfterRange(s, c.index)) {
      (s, c)
    } else if (ArrayMoveRangeHelper.isForwardMove(s)) {
      transformAgainstForwardMove(s, c)
    } else if (ArrayMoveRangeHelper.isBackwardMoveMove(s)) {
      transformAgainstBackwardMove(s, c)
    } else {
      ??? //FIXME
    }
  }

  def transformAgainstForwardMove(s: ArrayMoveOperation, c: ArrayReplaceOperation): (ArrayMoveOperation, ArrayReplaceOperation) = {
    if (s.fromIndex == c.index) {
      (s, c.copy(index = s.toIndex))
    } else if (ArrayMoveRangeHelper.indexWithinRange(s, c.index)
      || s.toIndex == c.index) {
      (s, c.copy(index = c.index - 1))
    } else {
      (s, c)
    }
  }

  def transformAgainstBackwardMove(s: ArrayMoveOperation, c: ArrayReplaceOperation): (ArrayMoveOperation, ArrayReplaceOperation) = {
    if (s.fromIndex == c.index) {
      (s, c.copy(index = s.toIndex))
    } else if (ArrayMoveRangeHelper.indexWithinRange(s, c.index) || s.toIndex == c.index) {
      (s, c.copy(index = c.index + 1))
    } else {
      (s, c)
    }
  }
}