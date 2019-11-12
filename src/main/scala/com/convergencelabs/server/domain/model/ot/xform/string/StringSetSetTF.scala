/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

package com.convergencelabs.server.domain.model.ot

private[ot] object StringSetSetTF extends OperationTransformationFunction[StringSetOperation, StringSetOperation] {
  def transform(s: StringSetOperation, c: StringSetOperation): (StringOperation, StringOperation) = {
    if (s.value == c.value) {
      // S-SS-1
      (s.copy(noOp = true), s.copy(noOp = true))
    } else {
      // S-SS-2
      (s, c.copy(noOp = true))
    }
  }
}
