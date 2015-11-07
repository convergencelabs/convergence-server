package com.convergencelabs.server.domain.model.ot.xform

import com.convergencelabs.server.domain.model.ot.ops.ArrayInsertOperation
import com.convergencelabs.server.domain.model.ot.ops.ArrayMoveOperation

object ArrayMoveInsertTF extends OperationTransformationFunction[ArrayMoveOperation, ArrayInsertOperation] {
  def transform(s: ArrayMoveOperation, c: ArrayInsertOperation): (ArrayMoveOperation, ArrayInsertOperation) = {
    if (ArrayMoveRangeHelper.isIdentityMove(s)) {
      (s, c)
    } else if (ArrayMoveRangeHelper.indexAfterRange(s, c.index)) {
      (s, c)
    } else if (ArrayMoveRangeHelper.indexBeforeRange(s, c.index)) {
      (s.copy(fromIndex = s.fromIndex + 1, toIndex = s.toIndex + 1), c)
    } else if (ArrayMoveRangeHelper.isForwardMove(s)) {
      transformAgainstForwardMove(s, c)
    } else if (ArrayMoveRangeHelper.isBackwardMoveMove(s)) {
      transformAgainstBackwardMove(s, c)
    } else {
      ??? // FIXME Exception, this should not happen
    }
  }

  def transformAgainstForwardMove(s: ArrayMoveOperation, c: ArrayInsertOperation): (ArrayMoveOperation, ArrayInsertOperation) = {
    if (s.fromIndex == c.index) {
      (s.copy(fromIndex = s.fromIndex + 1, toIndex = s.toIndex + 1), c)
    } else if (ArrayMoveRangeHelper.indexWithinRange(s, c.index) || s.toIndex == c.index) {
      (s.copy(toIndex = s.toIndex + 1), c.copy(index = c.index - 1))
    } else {
      (s, c)
    }
  }

  def transformAgainstBackwardMove(s: ArrayMoveOperation, c: ArrayInsertOperation): (ArrayMoveOperation, ArrayInsertOperation) = {
    if (s.fromIndex == c.index
      || ArrayMoveRangeHelper.indexWithinRange(s, c.index)
      || s.toIndex == c.index) {
      (s.copy(fromIndex = s.fromIndex + 1), c.copy(index = c.index + 1))
    } else {
      (s, c)
    }
  }
}