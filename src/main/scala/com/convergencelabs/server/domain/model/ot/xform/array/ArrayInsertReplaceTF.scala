/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

package com.convergencelabs.server.domain.model.ot

/**
 * This transformation function handles a concurrent server
 * ArrayInsertOperation and a client ArrayReplaceOperation.
 */
private[ot] object ArrayInsertReplaceTF extends OperationTransformationFunction[ArrayInsertOperation, ArrayReplaceOperation] {
  def transform(s: ArrayInsertOperation, c: ArrayReplaceOperation): (ArrayInsertOperation, ArrayReplaceOperation) = {
    if (s.index <= c.index) {
      // A-IP-1 and A-IP-2
      (s, c.copy(index = c.index + 1))
    } else {
      // A-IP-3
      (s, c)
    }
  }
}
