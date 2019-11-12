/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

package com.convergencelabs.server.domain.model.ot

private[ot] object ArrayReplaceSetTF extends OperationTransformationFunction[ArrayReplaceOperation, ArraySetOperation] {
  def transform(s: ArrayReplaceOperation, c: ArraySetOperation): (ArrayReplaceOperation, ArraySetOperation) = {
    // A-PS-1
    (s.copy(noOp = true), c)
  }
}
