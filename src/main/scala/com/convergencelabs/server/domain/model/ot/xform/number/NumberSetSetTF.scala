/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

package com.convergencelabs.server.domain.model.ot

private[ot] object NumberSetSetTF extends OperationTransformationFunction[NumberSetOperation, NumberSetOperation] {
  def transform(s: NumberSetOperation, c: NumberSetOperation): (NumberSetOperation, NumberSetOperation) = {
    if (s.value != c.value) {
      // N-SS-1
      (s, c.copy(noOp = true))
    } else {
      // N-SS-2
      (s.copy(noOp = true), c.copy(noOp = true))
    }
  }
}
