/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

package com.convergencelabs.server.domain.model.ot

private[ot] object ObjectSetRemovePropertyTF extends OperationTransformationFunction[ObjectSetOperation, ObjectRemovePropertyOperation] {
  def transform(s: ObjectSetOperation, c: ObjectRemovePropertyOperation): (ObjectSetOperation, ObjectRemovePropertyOperation) = {
    // O-SR-1
    (s, c.copy(noOp = true))
  }
}
