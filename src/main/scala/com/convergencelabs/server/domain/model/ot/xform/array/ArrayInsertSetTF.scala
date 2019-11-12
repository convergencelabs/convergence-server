/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

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
