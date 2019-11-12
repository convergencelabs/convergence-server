/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

package com.convergencelabs.server.domain.model.ot

private[ot] object NumberAddAddTF extends OperationTransformationFunction[NumberAddOperation, NumberAddOperation] {
  def transform(s: NumberAddOperation, c: NumberAddOperation): (NumberAddOperation, NumberAddOperation) = {
    // N-AA-1
    (s, c)
  }
}
