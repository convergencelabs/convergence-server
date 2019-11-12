/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

package com.convergencelabs.server.domain.model.ot

private[ot] object ArrayRemoveReplaceTF extends OperationTransformationFunction[ArrayRemoveOperation, ArrayReplaceOperation] {
  def transform(s: ArrayRemoveOperation, c: ArrayReplaceOperation): (ArrayRemoveOperation, ArrayOperation) = {
    if (s.index < c.index) {
      // A-RP-1
      (s, c.copy(index = c.index - 1))
    } else if (s.index == c.index) {
      // A-RP-2
      val ArrayReplaceOperation(path, noOp, index, newValue) = c
      (s.copy(noOp = true), ArrayInsertOperation(path, noOp, index, newValue))
    }  else {
      // A-RP-3
      (s, c)
    }
  }
}
