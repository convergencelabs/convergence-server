package com.convergencelabs.server.domain.model.ot.xform.reference

import com.convergencelabs.server.domain.model.SetReference
import com.convergencelabs.server.domain.model.ot.ReferenceTransformationFunction
import com.convergencelabs.server.domain.model.ot.StringRemoveOperation
import com.convergencelabs.server.domain.model.ot.xform.IndexTransformer

object StringRemoveRangeTF extends ReferenceTransformationFunction[StringRemoveOperation] {
  def transform(op: StringRemoveOperation, setReference: SetReference): Option[SetReference] = {
    val ranges = setReference.values.asInstanceOf[List[(Int, Int)]]
    val xformedRanges = ranges map { range =>
      val xFormed = IndexTransformer.handleRemove(List(range._1, range._2), op.index, op.value.length)
      (xFormed(0), xFormed(1))
    }
    Some(setReference.copy(values = xformedRanges))
  }
}
