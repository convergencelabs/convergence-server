/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

package com.convergencelabs.server.domain.model.ot

private[ot] object ObjectSetAddPropertyTF extends OperationTransformationFunction[ObjectSetOperation, ObjectAddPropertyOperation] {
  def transform(s: ObjectSetOperation, c: ObjectAddPropertyOperation): (ObjectSetOperation, ObjectAddPropertyOperation) = {
    // O-SA-1
    (s, c.copy(noOp = true))
  }
}
