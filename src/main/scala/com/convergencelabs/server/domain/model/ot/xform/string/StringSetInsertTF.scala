/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

package com.convergencelabs.server.domain.model.ot

private[ot] object StringSetInsertTF extends OperationTransformationFunction[StringSetOperation, StringInsertOperation] {
  def transform(s: StringSetOperation, c: StringInsertOperation): (StringOperation, StringOperation) = {
    // S-SI-1
    (s, c.copy(noOp = true))
  }
}
