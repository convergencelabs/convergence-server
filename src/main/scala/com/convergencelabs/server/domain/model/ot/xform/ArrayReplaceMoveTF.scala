package com.convergencelabs.server.domain.model.ot.xform

import com.convergencelabs.server.domain.model.ot.ops.ArrayReplaceOperation
import com.convergencelabs.server.domain.model.ot.ops.DiscreteOperation
import com.convergencelabs.server.domain.model.ot.ops.ArrayMoveOperation

object ArrayReplaceMoveTF extends OperationTransformationFunction[ArrayReplaceOperation, ArrayMoveOperation] {
  def transform(s: ArrayReplaceOperation, c: ArrayMoveOperation): (ArrayReplaceOperation, ArrayMoveOperation) = {
    if (ArrayMoveRangeHelper.isIdentityMove(c)) {
      (s, c)
    } else if (ArrayMoveRangeHelper.indexBeforeRange(c, s.index)
      || ArrayMoveRangeHelper.indexAfterRange(c, s.index)) {
      (s, c)
    } else if (ArrayMoveRangeHelper.isForwardMove(c)) {
      transformAgainstForwardMove(s, c)
    } else if (ArrayMoveRangeHelper.isBackwardMoveMove(c)) {
      transformAgainstBackwardMove(s, c)
    } else {
      ??? // FIXME real exception
    }
  }

  def transformAgainstForwardMove(s: ArrayReplaceOperation, c: ArrayMoveOperation): (ArrayReplaceOperation, ArrayMoveOperation) = {
    if (c.fromIndex == s.index) {
      (s.copy(index = c.toIndex), c)
    } else if (ArrayMoveRangeHelper.indexWithinRange(c, s.index) || c.toIndex == s.index) {
      (s.copy(index = s.index - 1), c)
    } else {
      (s, c)
    }
  }

  def transformAgainstBackwardMove(s: ArrayReplaceOperation, c: ArrayMoveOperation): (ArrayReplaceOperation, ArrayMoveOperation) = {
    if (c.fromIndex == s.index) {
      (s.copy(index = c.toIndex), c)
    } else if (ArrayMoveRangeHelper.indexWithinRange(c, s.index) || c.toIndex == s.index) {
      (s.copy(index = s.index + 1), c)
    } else {
      (s, c)
    }
  }
}