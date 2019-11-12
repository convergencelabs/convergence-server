/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

package com.convergencelabs.server.domain.model.ot

private[ot] object DateSetSetTF extends OperationTransformationFunction[DateSetOperation, DateSetOperation] {
  def transform(s: DateSetOperation, c: DateSetOperation): (DateOperation, DateOperation) = {
    if (s.value == c.value) {
      // D-SS-1
      (s.copy(noOp = true), s.copy(noOp = true))
    } else {
      // D-SS-2
      (s, c.copy(noOp = true))
    }
  }
}
