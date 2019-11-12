/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

package com.convergencelabs.server.domain.model.ot

private[ot] object ArrayRemoveInsertTF extends OperationTransformationFunction[ArrayRemoveOperation, ArrayInsertOperation] {
  def transform(s: ArrayRemoveOperation, c: ArrayInsertOperation): (ArrayRemoveOperation, ArrayInsertOperation) = {
    if (s.index < c.index) {
      // A-RI-1
      (s, c.copy(index = c.index - 1))
    } else {
      // A-RI-2 and A-RI-3
      (s.copy(index = s.index + 1), c)
    }
  }
}
