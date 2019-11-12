/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

package com.convergencelabs.server.domain.model.ot

private[ot] object NumberAddSetTF extends OperationTransformationFunction[NumberAddOperation, NumberSetOperation] {
  def transform(s: NumberAddOperation, c: NumberSetOperation): (NumberAddOperation, NumberSetOperation) = {
    // N-AS-1
    (s.copy(noOp = true), c)
  }
}
