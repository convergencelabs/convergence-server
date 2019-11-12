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

object StringInsertRangeTF extends ReferenceTransformationFunction[StringInsertOperation] {
  def transform(op: StringInsertOperation, setReference: ReferenceValue): Option[ReferenceValue] = {
    val ranges = setReference.values.asInstanceOf[List[(Int, Int)]]
    
    val xFormedRanges = ranges map { range =>
      val xFormed = IndexTransformer.handleInsert(List(range._1, range._2), op.index, op.value.length)
      (xFormed(0), xFormed(1))
    }

    Some(setReference.copy(values = xFormedRanges))
  }
}
