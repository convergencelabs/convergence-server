/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

package com.convergencelabs.server.domain.model.ot

private[ot] object BooleanSetSetTF extends OperationTransformationFunction[BooleanSetOperation, BooleanSetOperation] {
  def transform(s: BooleanSetOperation, c: BooleanSetOperation): (BooleanSetOperation, BooleanSetOperation) = {
    if (s.value != c.value) {
      // B-SS-1
      (s, c.copy(noOp = true))
    } else {
      // B-SS-2
      (s.copy(noOp = true), c.copy(noOp = true))
    }
  }
}
