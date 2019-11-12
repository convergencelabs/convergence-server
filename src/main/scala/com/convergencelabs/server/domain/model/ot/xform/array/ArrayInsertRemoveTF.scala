/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

package com.convergencelabs.server.domain.model.ot

/**
 * This transformation function handles the transformation from a concurrent
 * server ArrayInsertOperation and a client ArrayRemoveOperation.  The primary
 * determination of the action to take is based on the relative location of
 * the insert and remove indices.
 */
private[ot] object ArrayInsertRemoveTF extends OperationTransformationFunction[ArrayInsertOperation, ArrayRemoveOperation] {
  def transform(s: ArrayInsertOperation, c: ArrayRemoveOperation): (ArrayInsertOperation, ArrayRemoveOperation) = {
    if (s.index <= c.index) {
      // A-IR-1 and A-IR-2
      (s, c.copy(index = c.index + 1))
    } else {
      // A-IR-3
      (s.copy(index = s.index - 1), c)
    }
  }
}
