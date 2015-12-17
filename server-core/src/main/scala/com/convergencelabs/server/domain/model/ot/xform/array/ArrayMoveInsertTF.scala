package com.convergencelabs.server.domain.model.ot

private[ot] object ArrayMoveInsertTF extends OperationTransformationFunction[ArrayMoveOperation, ArrayInsertOperation] {
  
  def transform(s: ArrayMoveOperation, c: ArrayInsertOperation): (ArrayMoveOperation, ArrayInsertOperation) = {
    if (ArrayMoveRangeHelper.indexAfterRange(s, c.index)) {
      (s, c)
    } else if (ArrayMoveRangeHelper.indexBeforeRange(s, c.index)) {
      (s.copy(fromIndex = s.fromIndex + 1, toIndex = s.toIndex + 1), c)
    } else if (ArrayMoveRangeHelper.isForwardMove(s)) {
      transformAgainstForwardMove(s, c)
    } else if (ArrayMoveRangeHelper.isBackwardMoveMove(s)) {
      transformAgainstBackwardMove(s, c)
    } else {
      // Must be an identity move
      transformAgainstIdentityMove(s, c)
    }
  }

  private[this] def transformAgainstForwardMove(s: ArrayMoveOperation, c: ArrayInsertOperation): (ArrayMoveOperation, ArrayInsertOperation) = {
    // Given that from above we know that the insert is neither before nor after.  The index must
    // be at the start, end or within.
    if (s.fromIndex == c.index) {
      // At the start.
      (s.copy(fromIndex = s.fromIndex + 1, toIndex = s.toIndex + 1), c)
    } else {
      // At the end or within.
      (s.copy(toIndex = s.toIndex + 1), c.copy(index = c.index - 1))
    }
  }

  private[this] def transformAgainstBackwardMove(s: ArrayMoveOperation, c: ArrayInsertOperation): (ArrayMoveOperation, ArrayInsertOperation) = {
    // Given that from above we know that the insert is neither before nor after.  The index must
    // be at the start, end or within.

    // All three cases have the same transformation
    (s.copy(fromIndex = s.fromIndex + 1), c.copy(index = c.index + 1))
  }

  private[this] def transformAgainstIdentityMove(s: ArrayMoveOperation, c: ArrayInsertOperation): (ArrayMoveOperation, ArrayInsertOperation) = {
    // Given that from above we know that the insert is neither before nor 
    // after.  So it can only be at the same index.
    (s.copy(fromIndex = s.fromIndex + 1, toIndex = s.toIndex + 1), c)
  }
}
