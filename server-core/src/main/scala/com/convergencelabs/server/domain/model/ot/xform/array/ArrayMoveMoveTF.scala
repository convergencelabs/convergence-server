package com.convergencelabs.server.domain.model.ot

private[ot] object ArrayMoveMoveTF extends OperationTransformationFunction[ArrayMoveOperation, ArrayMoveOperation] {
  def transform(s: ArrayMoveOperation, c: ArrayMoveOperation): (ArrayMoveOperation, ArrayMoveOperation) = {

    if (ArrayMoveRangeHelper.isForwardMove(s) && ArrayMoveRangeHelper.isForwardMove(c)) {
      transformTwoForwardMoves(s, c)
    } else if (ArrayMoveRangeHelper.isForwardMove(s) && ArrayMoveRangeHelper.isBackwardMoveMove(c)) {
      transofrmForwardMoveWithBackwardMove(s, c)
    } else if (ArrayMoveRangeHelper.isBackwardMoveMove(s) && ArrayMoveRangeHelper.isForwardMove(c)) {
      transofrmBackwardMoveWithForwardMove(s, c)
    } else if (ArrayMoveRangeHelper.isBackwardMoveMove(s) && ArrayMoveRangeHelper.isBackwardMoveMove(c)) {
      transofrmBackwardMoveWithBackwardMove(s, c)
    } else {
      (s, c)
    }
  }

  def transofrmBackwardMoveWithBackwardMove(s: ArrayMoveOperation, c: ArrayMoveOperation): (ArrayMoveOperation, ArrayMoveOperation) = {
    if (ArrayMoveRangeHelper.precedes(s, c) || ArrayMoveRangeHelper.precededBy(s, c)) {
      (s, c)
    } else if (ArrayMoveRangeHelper.meets(s, c) || ArrayMoveRangeHelper.overlaps(s, c)) {
      (s.copy(toIndex = s.toIndex + 1), c.copy(fromIndex = c.fromIndex + 1))
    } else if (ArrayMoveRangeHelper.finishedBy(s, c) || ArrayMoveRangeHelper.finishes(s, c)) {
      (s.copy(fromIndex = c.toIndex), c.copy(noOp = true))
    } else if (ArrayMoveRangeHelper.contains(s, c)) {
      (s.copy(fromIndex = s.fromIndex + 1, toIndex = s.toIndex + 1), c)
    } else if (ArrayMoveRangeHelper.starts(s, c) || ArrayMoveRangeHelper.containedBy(s, c)) {
      (s, c.copy(fromIndex = c.fromIndex + 1, toIndex = c.toIndex + 1))
    } else if (ArrayMoveRangeHelper.equalTo(s, c)) {
      (s.copy(noOp = true), c.copy(noOp = true))
    } else if (ArrayMoveRangeHelper.startedBy(s, c) ||
      ArrayMoveRangeHelper.overlappedBy(s, c) ||
      ArrayMoveRangeHelper.metBy(s, c)) {
      (s.copy(fromIndex = s.fromIndex + 1), c.copy(toIndex = c.toIndex + 1))
    } else {
      ??? // FIXME Unanticipated case
    }
  }

  def transofrmBackwardMoveWithForwardMove(s: ArrayMoveOperation, c: ArrayMoveOperation): (ArrayMoveOperation, ArrayMoveOperation) = {
    if (ArrayMoveRangeHelper.precedes(s, c) || ArrayMoveRangeHelper.precededBy(s, c)) {
      (s, c) 
    } else if (ArrayMoveRangeHelper.meets(s, c)) {
      (s.copy(fromIndex = c.toIndex), c.copy(noOp = true))
    } else if (ArrayMoveRangeHelper.overlaps(s, c) ||
      ArrayMoveRangeHelper.finishedBy(s, c) ||
      ArrayMoveRangeHelper.starts(s, c) ||
      ArrayMoveRangeHelper.equalTo(s, c)) {
      (s.copy(fromIndex = s.fromIndex + 1), c.copy(fromIndex = c.fromIndex - 1))
    } else if (ArrayMoveRangeHelper.contains(s, c) ||
      ArrayMoveRangeHelper.startedBy(s, c)) {
      (s.copy(fromIndex = s.fromIndex + 1, toIndex = s.toIndex + 1), c)
    } else if (ArrayMoveRangeHelper.containedBy(s, c) ||
      ArrayMoveRangeHelper.finishes(s, c)) {
      (s, c.copy(fromIndex = c.fromIndex + 1, toIndex = c.toIndex + 1))
    } else if (ArrayMoveRangeHelper.overlappedBy(s, c) ||
      ArrayMoveRangeHelper.metBy(s, c)) {
      (s.copy(toIndex = s.toIndex + 1), c.copy(toIndex = c.toIndex - 1))
    } else {
      ??? // FIXME unanticipated case
    }
  }

  def transofrmForwardMoveWithBackwardMove(s: ArrayMoveOperation, c: ArrayMoveOperation): (ArrayMoveOperation, ArrayMoveOperation) = {
    if (ArrayMoveRangeHelper.precedes(s, c) || ArrayMoveRangeHelper.precededBy(s, c)) {
      (s, c)
    } else if (ArrayMoveRangeHelper.meets(s, c) || ArrayMoveRangeHelper.overlaps(s, c)) {
      (s.copy(toIndex = s.toIndex - 1), c.copy(toIndex = c.toIndex + 1))
    } else if (ArrayMoveRangeHelper.finishedBy(s, c) ||
      ArrayMoveRangeHelper.contains(s, c)) {
      (s.copy(fromIndex = s.fromIndex -1, toIndex = s.toIndex - 1), c)
    } else if (ArrayMoveRangeHelper.starts(s, c) ||
      ArrayMoveRangeHelper.containedBy(s, c)) {
      (s, c.copy(fromIndex = c.fromIndex + 1, toIndex = c.toIndex + 1))
    } else if (ArrayMoveRangeHelper.equalTo(s, c) ||
      ArrayMoveRangeHelper.finishes(s, c) ||
      ArrayMoveRangeHelper.startedBy(s, c) ||
      ArrayMoveRangeHelper.overlappedBy(s, c)) {
      (s.copy(fromIndex = s.fromIndex - 1), c.copy(fromIndex = c.fromIndex + 1))
    } else if (ArrayMoveRangeHelper.metBy(s, c)) {
      (s.copy(fromIndex = c.toIndex), c.copy(noOp = true))
    } else {
      ??? // FIXME Unanticipated case
    }
  }

  def transformTwoForwardMoves(s: ArrayMoveOperation, c: ArrayMoveOperation): (ArrayMoveOperation, ArrayMoveOperation) = {
    if (ArrayMoveRangeHelper.precedes(s, c) || ArrayMoveRangeHelper.precededBy(s, c)) {
      (s, c)
    } else if (ArrayMoveRangeHelper.meets(s, c) ||
      ArrayMoveRangeHelper.overlaps(s, c) ||
      ArrayMoveRangeHelper.finishedBy(s, c)) {
      (s.copy(fromIndex = s.fromIndex - 1), c.copy(toIndex = c.toIndex - 1))
    } else if (ArrayMoveRangeHelper.contains(s, c)) {
      (s.copy(fromIndex = s.fromIndex - 1, toIndex = s.toIndex - 1), c)
    } else if (ArrayMoveRangeHelper.starts(s, c) ||
      ArrayMoveRangeHelper.startedBy(s, c)) {
      (s.copy(fromIndex = c.toIndex), c.copy(noOp = true))
    } else if (ArrayMoveRangeHelper.equalTo(s, c)) {
      (s.copy(noOp = true), c.copy(noOp = true))
    } else if (ArrayMoveRangeHelper.containedBy(s, c) ||
      ArrayMoveRangeHelper.finishes(s, c)) {
      (s, c.copy(fromIndex = c.fromIndex - 1, toIndex = c.toIndex - 1))
    } else if (ArrayMoveRangeHelper.overlappedBy(s, c) ||
      ArrayMoveRangeHelper.metBy(s, c)) {
      (s.copy(toIndex = s.toIndex - 1), c.copy(fromIndex = c.fromIndex - 1))
    } else {
      ??? // FIXME Unanticipated case
    }
  }
}