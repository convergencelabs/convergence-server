package com.convergencelabs.server.domain.model.ot

private[ot] object ArrayInsertMoveTF extends OperationTransformationFunction[ArrayInsertOperation, ArrayMoveOperation] {
  def transform(s: ArrayInsertOperation, c: ArrayMoveOperation): (ArrayInsertOperation, ArrayMoveOperation) = {
     if (ArrayMoveRangeHelper.indexAfterRange(c, s.index)) {
      (s, c)
    } else if (ArrayMoveRangeHelper.indexBeforeRange(c, s.index)) {
      (s, c.copy(fromIndex = c.fromIndex + 1, toIndex = c.toIndex + 1))
    } else if (ArrayMoveRangeHelper.isForwardMove(c)) {
      transformAgainstForwardMove(s, c)
    } else if (ArrayMoveRangeHelper.isBackwardMoveMove(c)) {
      transformAgainstBackwardMove(s, c)
    } else {
      // Must be an identity move
      transformAgainstIdentityMove(s, c)
    }
  }

  def transformAgainstForwardMove(s: ArrayInsertOperation, c: ArrayMoveOperation): (ArrayInsertOperation, ArrayMoveOperation) = {
    if (c.fromIndex == s.index) {
      (s, c.copy(fromIndex = c.fromIndex + 1, toIndex = c.toIndex + 1))
    } else {
      // ArrayMoveRangeHelper.indexWithinRange(c, s.index) || c.toIndex == s.index
      (s.copy(index = s.index - 1), c.copy(toIndex = c.toIndex + 1))
    } 
  }

  def transformAgainstBackwardMove(s: ArrayInsertOperation, c: ArrayMoveOperation): (ArrayInsertOperation, ArrayMoveOperation) = {
    if (c.fromIndex == s.index || ArrayMoveRangeHelper.indexWithinRange(c, s.index)) {
      (s.copy(index = s.index + 1), c.copy(fromIndex = c.fromIndex + 1))
    } else {
      // c.toIndex == s.index
      (s, c.copy(fromIndex = c.fromIndex + 1, toIndex = c.toIndex + 1))
    } 
  }
  
  private[this] def transformAgainstIdentityMove(s: ArrayInsertOperation, c: ArrayMoveOperation): (ArrayInsertOperation, ArrayMoveOperation) = {
    // Given that from above we know that the insert is neither before nor 
    // after.  So it can only be at the same index.
    (s, c.copy(fromIndex = c.fromIndex + 1, toIndex = c.toIndex + 1))
  }
}
