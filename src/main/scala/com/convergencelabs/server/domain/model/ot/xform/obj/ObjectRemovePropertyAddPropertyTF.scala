/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

package com.convergencelabs.server.domain.model.ot

private[ot] object ObjectRemovePropertyAddPropertyTF extends OperationTransformationFunction[ObjectRemovePropertyOperation, ObjectAddPropertyOperation] {
  def transform(s: ObjectRemovePropertyOperation, c: ObjectAddPropertyOperation): (ObjectRemovePropertyOperation, ObjectAddPropertyOperation) = {
    if (s.property != c.property) {
      // O-RA-1
      (s, c)
    } else {
      // O-RA-2
      throw new IllegalArgumentException("Remove property and add property can not target the same property")
    }
  }
}
