package com.convergencelabs.server.domain.model.ot.xform.reference

import com.convergencelabs.server.domain.model.SetReference
import com.convergencelabs.server.domain.model.ot.ReferenceTransformationFunction
import com.convergencelabs.server.domain.model.ot.StringInsertOperation
import com.convergencelabs.server.domain.model.ot.xform.IndexTransformer

object StringInsertRangeTF extends ReferenceTransformationFunction[StringInsertOperation] {
  def transform(op: StringInsertOperation, setReference: SetReference): Option[SetReference] = {
    val range = setReference.value.asInstanceOf[(Int, Int)]
    val xFormed = IndexTransformer.handleInsert(List(range._1, range._2), op.index, op.value.length)
    Some(setReference.copy(value = (xFormed(0), xFormed(1)) ))
  }
}
