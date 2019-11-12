/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

package com.convergencelabs.server.domain.model.ot

private[ot] object ArraySetSetTF extends OperationTransformationFunction[ArraySetOperation, ArraySetOperation] {
  def transform(s: ArraySetOperation, c: ArraySetOperation): (ArraySetOperation, ArraySetOperation) = {
    if (s.value != c.value) {
      // A-SS-1
      (s, c.copy(noOp = true))
    } else {
      // A-SS-2
      (s.copy(noOp = true), c.copy(noOp = true))
    }
  }
}
