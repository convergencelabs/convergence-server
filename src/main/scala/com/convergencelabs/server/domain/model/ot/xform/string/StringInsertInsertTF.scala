/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

package com.convergencelabs.server.domain.model.ot

/**
 * This transformation function handles the case where a server
 * StringInsertOperation is concurrent with a client StringInsertOperation.
 * The major consideration in determining what transformation path to take
 * is the relative position of the two operation's positional indices.
 */
private[ot] object StringInsertInsertTF extends OperationTransformationFunction[StringInsertOperation, StringInsertOperation] {
  def transform(s: StringInsertOperation, c: StringInsertOperation): (StringOperation, StringOperation) = {
    if (s.index <= c.index) {
      // S-II-1 and S-II-2
      (s, c.copy(index = c.index + s.value.length))
    } else {
      // S-II-3
      (s.copy(index = s.index + c.value.length), c)
    }
  }
}
