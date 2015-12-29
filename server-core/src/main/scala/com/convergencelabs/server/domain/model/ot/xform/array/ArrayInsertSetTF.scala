package com.convergencelabs.server.domain.model.ot

/**
 * This transformation function handles a concurrent server 
 * ArrayInsertOperation and a client ArraySetOperation.  In every case, the 
 * set operation will be preserved while the insert will be No-Op'ed
 */
private[ot] object ArrayInsertSetTF extends OperationTransformationFunction[ArrayInsertOperation, ArraySetOperation] {
  def transform(s: ArrayInsertOperation, c: ArraySetOperation): (ArrayInsertOperation, ArraySetOperation) = {
    // A-IS-1
    (s.copy(noOp = true), c)
  }
}
