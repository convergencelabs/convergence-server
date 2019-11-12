/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

package com.convergencelabs.server.domain.model.ot

private[ot] object StringRemoveInsertTF extends OperationTransformationFunction[StringRemoveOperation, StringInsertOperation] {
  def transform(s: StringRemoveOperation, c: StringInsertOperation): (StringOperation, StringOperation) = {
    if (c.index <= s.index) {
      // S-RI-1 and S-RI-2
      (s.copy(index = s.index + c.value.length), c)
    } else if (c.index >= s.index + s.value.length) {
      // S-RI-5
      (s, c.copy(index = c.index - s.value.length))
    } else {
      // S-RI-3 and S-RI-4
      val offsetDelta = c.index - s.index
      (s.copy(value =
        s.value.substring(0, offsetDelta) +
          c.value +
          s.value.substring(offsetDelta, s.value.length)),
        c.copy(noOp = true))
    }
  }
}
