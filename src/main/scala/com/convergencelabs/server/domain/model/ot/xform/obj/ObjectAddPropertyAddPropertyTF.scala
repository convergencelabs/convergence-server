/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

package com.convergencelabs.server.domain.model.ot

private[ot] object ObjectAddPropertyAddPropertyTF extends OperationTransformationFunction[ObjectAddPropertyOperation, ObjectAddPropertyOperation] {
  def transform(s: ObjectAddPropertyOperation, c: ObjectAddPropertyOperation): (ObjectOperation, ObjectAddPropertyOperation) = {
    if (s.property != c.property) {
      // O-AA-1
      (s, c)
    } else if (s.value != c.value) {
      // O-AA-2
      val ObjectAddPropertyOperation(path, noOp, prop, value) = s
      (ObjectSetPropertyOperation(path, noOp, prop, value), c.copy(noOp = true))
    } else {
      // O-AA-3
      (s.copy(noOp = true), c.copy(noOp = true))
    }
  }
}
