/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

package com.convergencelabs.server.domain.model.ot

private[ot] object ObjectAddPropertySetPropertyTF extends OperationTransformationFunction[ObjectAddPropertyOperation, ObjectSetPropertyOperation] {
  def transform(s: ObjectAddPropertyOperation, c: ObjectSetPropertyOperation): (ObjectAddPropertyOperation, ObjectSetPropertyOperation) = {
    if (s.property != c.property) {
      // O-AT-1
      (s, c)
    } else {
      // O-AT-2
      throw new IllegalArgumentException("Add property and set property can not target the same property")
    }
  }
}
