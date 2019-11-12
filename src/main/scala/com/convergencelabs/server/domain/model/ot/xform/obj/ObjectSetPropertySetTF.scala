/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

package com.convergencelabs.server.domain.model.ot

private[ot] object ObjectSetPropertySetTF extends OperationTransformationFunction[ObjectSetPropertyOperation, ObjectSetOperation] {
  def transform(s: ObjectSetPropertyOperation, c: ObjectSetOperation): (ObjectSetPropertyOperation, ObjectSetOperation) = {
    // O-TS-1
    (s.copy(noOp = true), c)
  }
}
