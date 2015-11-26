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

  /**
   * Determines if one op 'precedes' another. Note that if either range is an empty range
   * this method will false. The precedes relationship is depicted below.
   *
   * <pre>
   * op1 ..|..|.......
   * op2 .......|..|..
   * </pre>
   *
   * @param op1
   *            The op that is the subject of the verb.
   * @param op2
   *            The op that is the object of the verb
   * @return True if op1 precedes op2, false otherwise.
   */
  def precedes(op1: ArrayMoveOperation, op2: ArrayMoveOperation): Boolean = {
    getRangeMax(op1) < getRangeMin(op2)
  }

  /**
   * Determines if one op is 'preceded by' another. Note that if either range is an empty
   * range this method will false. The preceded by relationship is depicted below.
   *
   * <pre>
   * op1 .......|..|..
   * op2 ..|..|.......
   * </pre>
   *
   * @param op1
   *            The op that is the subject of the verb.
   * @param op2
   *            The op that is the object of the verb
   * @return True if op1 is preceded by op2, false otherwise.
   */
  def precededBy(op1: ArrayMoveOperation, op2: ArrayMoveOperation): Boolean = {
    getRangeMax(op2) < getRangeMin(op1)
  }

  /**
   * Determines if one op 'meets' another. Note that if either range is an empty range this
   * method will false. The meets relationship is depicted below.
   *
   * <pre>
   * op1 ..|...|......
   * op2 ......|...|..
   * </pre>
   *
   * @param op1
   *            The op that is the subject of the verb.
   * @param op2
   *            The op that is the object of the verb
   * @return True if op1 meets op2, false otherwise.
   */
  def meets(op1: ArrayMoveOperation, op2: ArrayMoveOperation): Boolean = {
    getRangeMax(op1) == getRangeMin(op2)
  }

  /**
   * Determines if one op is 'met by' another. Note that if either range is an empty range
   * this method will false. The met by relationship is depicted below.
   *
   * <pre>
   * op1 ......|...|..
   * op2 ..|...|......
   * </pre>
   *
   * @param op1
   *            The op that is the subject of the verb.
   * @param op2
   *            The op that is the object of the verb
   * @return True if op1 is met by op2, false otherwise.
   */
  def metBy(op1: ArrayMoveOperation, op2: ArrayMoveOperation): Boolean = {
    getRangeMin(op1) == getRangeMax(op2)
  }

  /**
   * Determines if one op 'overlaps' another. Note that if either range is an empty range
   * this method will false. The overlaps relationship is depicted below.
   *
   * <pre>
   * op1 ..|...|......
   * op2 ....|...|....
   * </pre>
   *
   * @param op1
   *            The op that is the subject of the verb.
   * @param op2
   *            The op that is the object of the verb
   * @return True if op1 is overlaps op2, false otherwise.
   */
  def overlaps(op1: ArrayMoveOperation, op2: ArrayMoveOperation): Boolean = {
    indexBeforeRange(op2, getRangeMin(op1)) && indexWithinRange(op2, getRangeMax(op1))
  }

  /**
   * Determines if one op is 'overlapped by' another. Note that if either range is an empty
   * range this method will false. The overlapped by relationship is depicted below.
   *
   * <pre>
   * op1 ....|...|....
   * op2 ..|...|......
   * </pre>
   *
   * @param op1
   *            The op that is the subject of the verb.
   * @param op2
   *            The op that is the object of the verb
   * @return True if op1 is overlapped by op2, false otherwise.
   */
  def overlappedBy(op1: ArrayMoveOperation, op2: ArrayMoveOperation): Boolean = {
    indexBeforeRange(op1, getRangeMin(op2)) && indexWithinRange(op1, getRangeMax(op2))
  }

  /**
   * Determines if one op 'starts' another. Note that if either range is an empty range
   * this method will false. The starts by relationship is depicted below.
   *
   * <pre>
   * op1 ..|..|......
   * op2 ..|....|....
   * </pre>
   *
   * @param op1
   *            The op that is the subject of the verb.
   * @param op2
   *            The op that is the object of the verb
   * @return True if op1 starts op2, false otherwise.
   */
  def starts(op1: ArrayMoveOperation, op2: ArrayMoveOperation): Boolean = {
    getRangeMin(op1) == getRangeMin(op2) && getRangeMax(op2) > getRangeMax(op1)
  }

  /**
   * Determines if one op is 'started by' another. Note that if either range is an empty
   * range this method will false. The overlaps by relationship is depicted below.
   *
   * <pre>
   * op1 ..|....|....
   * op2 ..|..|......
   * </pre>
   *
   * @param op1
   *            The op that is the subject of the verb.
   * @param op2
   *            The op that is the object of the verb
   * @return True if op1 is started by op2, false otherwise.
   */
  def startedBy(op1: ArrayMoveOperation, op2: ArrayMoveOperation): Boolean = {
    getRangeMin(op1) == getRangeMin(op2) && getRangeMax(op1) > getRangeMax(op2)
  }

  /**
   * Determines if one op 'equals' another. The equals relationship is depicted below.
   *
   * <pre>
   * op1 ..|...|....
   * op2 ..|...|....
   * </pre>
   *
   * @param op1
   *            The op that is the subject of the verb.
   * @param op2
   *            The op that is the object of the verb
   * @return True if op1 is started by op2.
   */
  def equalTo(op1: ArrayMoveOperation, op2: ArrayMoveOperation): Boolean = {
    getRangeMin(op1) == getRangeMin(op2) && getRangeMax(op1) == getRangeMax(op2)
  }

  /**
   * Determines if one op 'contains' another. The contains relationship is depicted below.
   *
   * <pre>
   * op1 ...|....|....
   * op2 ....|..|.....
   * </pre>
   *
   * @param op1
   *            The op that is the subject of the verb.
   * @param op2
   *            The op that is the object of the verb
   * @return True if op1 contains op2.
   */
  def contains(op1: ArrayMoveOperation, op2: ArrayMoveOperation): Boolean = {
    indexWithinRange(op1, getRangeMin(op2)) && indexWithinRange(op1, getRangeMax(op2))
  }

  /**
   * Determines if one op is 'contained by' another. The contained by relationship is
   * depicted below.
   *
   * <pre>
   * op1 ....|..|......
   * op2 ...|....|.....
   * </pre>
   *
   * @param op1
   *            The op that is the subject of the verb.
   * @param op2
   *            The op that is the object of the verb
   * @return True if op1 is contained by op2, false otherwise.
   */
  def containedBy(op1: ArrayMoveOperation, op2: ArrayMoveOperation): Boolean = {
    indexWithinRange(op2, getRangeMin(op1)) && indexWithinRange(op2, getRangeMax(op1))
  }

  /**
   * Determines if one op 'finishes' another. Note that if either range is an empty range
   * this method will false. The starts by relationship is depicted below.
   *
   * <pre>
   * op1 ....|..|....
   * op2 ..|....|....
   * </pre>
   *
   * @param op1
   *            The op that is the subject of the verb.
   * @param op2
   *            The op that is the object of the verb
   * @return True if op1 finishes op2, false otherwise.
   */
  def finishes(op1: ArrayMoveOperation, op2: ArrayMoveOperation): Boolean = {
    getRangeMax(op1) == getRangeMax(op2) && getRangeMin(op2) < getRangeMin(op1)
  }

  /**
   * Determines if one op is 'finished by' another. Note that if either range is an empty
   * range this method will false. The starts by relationship is depicted below.
   *
   * <pre>
   * op1 ..|....|....
   * op2 ....|..|....
   * </pre>
   *
   * @param op1
   *            The op that is the subject of the verb.
   * @param op2
   *            The op that is the object of the verb
   * @return True if op1 finished by op2, false otherwise.
   */
  def finishedBy(op1: ArrayMoveOperation, op2: ArrayMoveOperation): Boolean = {
    getRangeMax(op1) == getRangeMax(op2) && getRangeMin(op1) < getRangeMin(op2)
  }

  /**
   * Returns the lesser of the fromIndex and the toIndex of the ArrayMoveOperation
   *
   * @param op
   *            The op to get the minimum index for
   * @return min(fromIndex, toIndex)
   */
  def getRangeMin(op: ArrayMoveOperation): Long = {
    Math.min(op.fromIndex, op.toIndex)
  }

  /**
   * Returns the greater of the fromIndex and the toIndex of the ArrayMoveOperation
   *
   * @param op
   *            The op to get the minimum index for
   * @return max(fromIndex, toIndex)
   */
  def getRangeMax(op: ArrayMoveOperation): Long = {
    Math.max(op.fromIndex, op.toIndex)
  }
}