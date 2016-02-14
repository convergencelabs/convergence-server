package com.convergencelabs.server.domain.model.ot

/**
 * This transformation function handles the case where a server
 * [[ArrayInsertOperation]] is concurrent with a client ArrayInsertOperation.
 * The main criteria for the transformation rules depend in the relative
 * position of the two operations.
 */
private[ot] object ArrayInsertInsertTF extends OperationTransformationFunction[ArrayInsertOperation, ArrayInsertOperation] {
  def transform(s: ArrayInsertOperation, c: ArrayInsertOperation): (ArrayInsertOperation, ArrayInsertOperation) = {
    if (s.index <= c.index) {
      // A-II-1 and A-II-2
      (s, c.copy(index = c.index + 1))
    } else {
      // A-II-3
      (s.copy(index = s.index + 1), c)
    }
  }
}
