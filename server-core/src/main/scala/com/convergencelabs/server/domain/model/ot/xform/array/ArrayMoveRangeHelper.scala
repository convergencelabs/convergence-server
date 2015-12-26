package com.convergencelabs.server.domain.model.ot

/**
 * A helper class to evaluate positional relationships with Array Move Operations.
 */
private[ot] object ArrayMoveRangeHelper {

  /**
   * Determines if a move op is in the forward direction. For this to be true the from
   * index must be strictly less than the to index.
   *
   * @param op
   *            The op to evaluate
   * @return true if fromIndex < toIndex, false otherwise.
   */
  def isForwardMove(op: ArrayMoveOperation): Boolean = {
    op.fromIndex < op.toIndex
  }

  /**
   * Determines if a move op is in the backward direction. For this to be true the from
   * index must be strictly greater than the to index.
   *
   * @param op
   *            The op to evaluate
   * @return true if fromIndex > toIndex, false otherwise.
   */
  def isBackwardMoveMove(op: ArrayMoveOperation): Boolean = {
    op.fromIndex > op.toIndex
  }

  /**
   * Determines if a move op is an empty range. This means that the from and to indices are
   * equal.
   *
   * @param op
   *            The op to evaluate
   * @return true if fromIndex == toIndex, false otherwise.
   */
  def isIdentityMove(op: ArrayMoveOperation): Boolean = {
    op.fromIndex == op.toIndex
  }

  /**
   * Determines the direction of the move.
   *
   * @param op The operation to evaluate.
   *
   * @return The direction of the move.
   */
  def getMoveDirection(op: ArrayMoveOperation): MoveDirection.Value = {
    if (isForwardMove(op)) {
      MoveDirection.Forward
    } else if (isBackwardMoveMove(op)) {
      MoveDirection.Backward
    } else {
      MoveDirection.Identity
    }
  }

  /**
   * Determines if an index is entirely before a range.
   *
   * @param op
   *            The op that represents the range.
   * @param index
   *            The index to evaluate
   * @return True if index < min(fromIndex, toIndex), false otherwise.
   */
  def indexBeforeRange(op: ArrayMoveOperation, index: Long): Boolean = {
    index < getRangeMin(op)
  }

  /**
   * Determines if an index is entirely after a range.
   *
   * @param op
   *            The op that represents the range.
   * @param index
   *            The index to evaluate
   * @return True if index > max(fromIndex, toIndex), false otherwise.
   */
  def indexAfterRange(op: ArrayMoveOperation, index: Long): Boolean = {
    index > getRangeMax(op)
  }

  /**
   * Determines if an index is within a range.
   *
   * @param op
   *            The op that represents the range.
   * @param index
   *            The index to evaluate
   * @return True if index < max(fromIndex, toIndex) && index > min(fromIndex, toIndex), false
   *         otherwise.
   */
  def indexWithinRange(op: ArrayMoveOperation, index: Long): Boolean = {
    index > getRangeMin(op) && index < getRangeMax(op)
  }
  
  def getRangeIndexRelationship(op: ArrayMoveOperation, index: Int): RangeIndexRelationship.Value = {
    RangeRelationshipUtil.getRangeIndexRelationship(getRangeMin(op), getRangeMax(op), index)
  }

  /**
   * Gets the range relationship of two array move operations.  The evaluation
   * will be in the form of op1 <verb> op2. For example if op1 <precedes> op2
   * the Precedes will be returned.
   *
   * @param op1 The first array move operation
   * @param op2 The second array move operation
   *
   * @return The interval that matched op1 <verb> op2
   */
  def getRangeRelationship(op1: ArrayMoveOperation, op2: ArrayMoveOperation): RangeRangeRelationship.Value = {
    RangeRelationshipUtil.getRangeRangeRelationship(getRangeMin(op1), getRangeMax(op1), getRangeMin(op2), getRangeMax(op2))
  }
  
  /**
   * Returns the lesser of the fromIndex and the toIndex of the ArrayMoveOperation
   *
   * @param op
   *            The op to get the minimum index for
   * @return min(fromIndex, toIndex)
   */
  def getRangeMin(op: ArrayMoveOperation): Int = {
    Math.min(op.fromIndex, op.toIndex)
  }

  /**
   * Returns the greater of the fromIndex and the toIndex of the ArrayMoveOperation
   *
   * @param op
   *            The op to get the minimum index for
   * @return max(fromIndex, toIndex)
   */
  def getRangeMax(op: ArrayMoveOperation): Int = {
    Math.max(op.fromIndex, op.toIndex)
  }
}

object MoveDirection extends Enumeration {
  val Forward, Backward, Identity = Value
}