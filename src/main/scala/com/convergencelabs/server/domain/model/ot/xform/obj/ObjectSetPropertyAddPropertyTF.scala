/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

package com.convergencelabs.server.domain.model.ot

private[ot] object ObjectSetPropertyAddPropertyTF extends OperationTransformationFunction[ObjectSetPropertyOperation, ObjectAddPropertyOperation] {
  def transform(s: ObjectSetPropertyOperation, c: ObjectAddPropertyOperation): (ObjectSetPropertyOperation, ObjectAddPropertyOperation) = {
    if (s.property != c.property) {
      // O-TA-1
      (s, c)
    } else {
      // O-TA-2
      throw new IllegalArgumentException("Set property and add property can not target the same property")
    }
  }
}
