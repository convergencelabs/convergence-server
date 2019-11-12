/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

package com.convergencelabs.server.domain.model.ot

private[ot] object NumberSetAddTF extends OperationTransformationFunction[NumberSetOperation, NumberAddOperation] {
  def transform(s: NumberSetOperation, c: NumberAddOperation): (NumberSetOperation, NumberAddOperation) = {
    // N-SA-1
    (s, c.copy(noOp = true))
  }
}
