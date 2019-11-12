/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

package com.convergencelabs.server.domain.model.ot

private[ot] object ArraySetRemoveTF extends OperationTransformationFunction[ArraySetOperation, ArrayRemoveOperation] {
  def transform(s: ArraySetOperation, c: ArrayRemoveOperation): (ArraySetOperation, ArrayRemoveOperation) = {
    // A-SR-1
    (s, c.copy(noOp = true))
  }
}
