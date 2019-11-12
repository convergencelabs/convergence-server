/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

package com.convergencelabs.server.domain.model.ot

private[ot] object ObjectRemovePropertyRemovePropertyTF extends OperationTransformationFunction[ObjectRemovePropertyOperation, ObjectRemovePropertyOperation] {
  def transform(s: ObjectRemovePropertyOperation, c: ObjectRemovePropertyOperation): (ObjectRemovePropertyOperation, ObjectRemovePropertyOperation) = {
    if (s.property != c.property) {
      // O-RR-1
      (s, c)
    } else {
      // O-RR-2
      (s.copy(noOp = true), c.copy(noOp = true))
    }
  }
}
