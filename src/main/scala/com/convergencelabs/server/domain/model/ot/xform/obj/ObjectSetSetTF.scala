/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

package com.convergencelabs.server.domain.model.ot

private[ot] object ObjectSetSetTF extends OperationTransformationFunction[ObjectSetOperation, ObjectSetOperation] {
  def transform(s: ObjectSetOperation, c: ObjectSetOperation): (ObjectSetOperation, ObjectSetOperation) = {
    if (s.value != c.value) {
      // O-SS-1
      (s, c.copy(noOp = true))
    } else {
      // O-SS-2
      (s.copy(noOp = true), c.copy(noOp = true))
    }
  }
}
