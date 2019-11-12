/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

package com.convergencelabs.server.domain.model.ot.xform.reference

import com.convergencelabs.server.domain.model.ot.ReferenceTransformationFunction
import com.convergencelabs.server.domain.model.ot.StringInsertOperation
import com.convergencelabs.server.domain.model.ot.xform.IndexTransformer
import com.convergencelabs.server.domain.model.ReferenceValue

object StringInsertIndexTF extends ReferenceTransformationFunction[StringInsertOperation] {
  def transform(op: StringInsertOperation, setReference: ReferenceValue): Option[ReferenceValue] = {
    val indices = setReference.values.asInstanceOf[List[Int]]
    val xFormed = IndexTransformer.handleInsert(indices, op.index, op.value.length)
    Some(setReference.copy(values = xFormed))
  }
}
