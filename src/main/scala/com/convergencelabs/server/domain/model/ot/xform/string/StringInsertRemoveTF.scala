/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

package com.convergencelabs.server.domain.model.ot

private[ot] object StringInsertRemoveTF extends OperationTransformationFunction[StringInsertOperation, StringRemoveOperation] {
  def transform(s: StringInsertOperation, c: StringRemoveOperation): (StringOperation, StringOperation) = {
    if (s.index <= c.index) {
      // S-IR-1 and S-IR-2
      (s, c.copy(index = c.index + s.value.length))
    } else if (s.index >= c.index + c.value.length ) {
      // S-IR-5
      (s.copy(index = s.index - c.value.length), c)
    } else {
      // S-IR-3 and S-IR-4
      val offsetDelta = s.index - c.index
      (s.copy(noOp = true),
        c.copy(
          value = c.value.substring(0, offsetDelta) +
            s.value +
            c.value.substring(offsetDelta, c.value.length)))
    }
  }
}
