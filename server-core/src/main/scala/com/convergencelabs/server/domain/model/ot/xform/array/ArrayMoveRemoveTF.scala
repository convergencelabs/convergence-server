package com.convergencelabs.server.domain.model.ot

private[ot] object ArrayMoveRemoveTF extends OperationTransformationFunction[ArrayMoveOperation, ArrayRemoveOperation] {
  def transform(s: ArrayMoveOperation, c: ArrayRemoveOperation): (ArrayMoveOperation, ArrayRemoveOperation) = {
    if (ArrayMoveRangeHelper.indexBeforeRange(s, c.index)) {
      (s.copy(fromIndex = s.fromIndex - 1, toIndex = s.toIndex - 1), c)
    } else if (ArrayMoveRangeHelper.indexAfterRange(s, c.index)) {
      (s, c)
    } else if (ArrayMoveRangeHelper.isForwardMove(s)) {
      transformAgainstForwardMove(s, c)
    } else if (ArrayMoveRangeHelper.isBackwardMoveMove(s)) {
      transformAgainstBackwardMove(s, c)
    } else {
      // Identity Move
      transformAgainstIdentityMove(s, c)
    }
  }

  def transformAgainstForwardMove(s: ArrayMoveOperation, c: ArrayRemoveOperation): (ArrayMoveOperation, ArrayRemoveOperation) = {
    // There are only three cases here.  Either the remove is at the start, the end, or in the middle.
    if (s.fromIndex == c.index) {
      // At the start.
      (s.copy(noOp = true), c.copy(index = s.toIndex))
    } else {
      // At the end or in the middle.
      (s.copy(toIndex = s.toIndex - 1), c.copy(index = c.index - 1))
    } 
  }

  def transformAgainstBackwardMove(s: ArrayMoveOperation, c: ArrayRemoveOperation): (ArrayMoveOperation, ArrayRemoveOperation) = {
    // There are only three cases here.  Either the remove is at the start, the end, or in the middle.
    if (s.fromIndex == c.index) {
      // At the start.
      (s.copy(noOp = true), c.copy(index = s.toIndex))
    } else {
      // At the end or in the middle.
      (s.copy(fromIndex = s.fromIndex - 1), c.copy(index = c.index + 1))
    }
  }
  
   def transformAgainstIdentityMove(s: ArrayMoveOperation, c: ArrayRemoveOperation): (ArrayMoveOperation, ArrayRemoveOperation) = {
     // Since this is an identity move, and we know the remove is not before or after, than
     // it must be pointed at the start / end. Only one case.
    (s.copy(noOp = true), c)
  }
}
