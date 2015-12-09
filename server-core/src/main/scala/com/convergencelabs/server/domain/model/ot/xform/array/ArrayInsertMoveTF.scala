package com.convergencelabs.server.domain.model.ot

private[ot] object ArrayInsertMoveTF extends OperationTransformationFunction[ArrayInsertOperation, ArrayMoveOperation] {
  def transform(s: ArrayInsertOperation, c: ArrayMoveOperation): (ArrayInsertOperation, ArrayMoveOperation) = {
    if (ArrayMoveRangeHelper.isIdentityMove(c)) {
      (s, c)
    } else if (ArrayMoveRangeHelper.indexAfterRange(c, s.index)) {
      (s, c)
    } else if (ArrayMoveRangeHelper.indexBeforeRange(c, s.index)) {
      (s, c.copy(fromIndex = c.fromIndex + 1, toIndex = c.toIndex + 1))
    } else if (ArrayMoveRangeHelper.isForwardMove(c)) {
      transformAgainstForwardMove(s, c)
    } else if (ArrayMoveRangeHelper.isBackwardMoveMove(c)) {
      transformAgainstBackwardMove(s, c)
    } else {
      throw new UnsupportedOperationException(s"An unanticipated Insert-Move case was detected ($s, $c)")
    }
  }

  def transformAgainstForwardMove(s: ArrayInsertOperation, c: ArrayMoveOperation): (ArrayInsertOperation, ArrayMoveOperation) = {
    if (c.fromIndex == s.index) {
      (s, c.copy(fromIndex = c.fromIndex + 1, toIndex = c.toIndex + 1))
    } else if (ArrayMoveRangeHelper.indexWithinRange(c, s.index) || c.toIndex == s.index) {
      (s.copy(index = s.index - 1), c.copy(toIndex = c.toIndex + 1))
    } else {
      (s, c)
    }
  }

  def transformAgainstBackwardMove(s: ArrayInsertOperation, c: ArrayMoveOperation): (ArrayInsertOperation, ArrayMoveOperation) = {
    if (c.fromIndex == s.index || ArrayMoveRangeHelper.indexWithinRange(c, s.index)) {
      (s.copy(index = s.index + 1), c.copy(fromIndex = c.fromIndex + 1))
    } else if (c.toIndex == s.index) {
      (s, c.copy(fromIndex = c.fromIndex + 1, toIndex = c.toIndex + 1))
    } else {
      (s, c)
    }
  }
}
