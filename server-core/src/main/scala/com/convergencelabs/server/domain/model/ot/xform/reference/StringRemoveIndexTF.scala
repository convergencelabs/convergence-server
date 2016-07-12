package com.convergencelabs.server.domain.model.ot.xform.reference

import com.convergencelabs.server.domain.model.SetReference
import com.convergencelabs.server.domain.model.ot.ReferenceTransformationFunction
import com.convergencelabs.server.domain.model.ot.StringRemoveOperation
import com.convergencelabs.server.domain.model.ot.xform.IndexTransformer

object StringRemoveIndexTF extends ReferenceTransformationFunction[StringRemoveOperation] {
  def transform(op: StringRemoveOperation, setReference: SetReference): Option[SetReference] = {
    val index = setReference.value.asInstanceOf[Int]
    val xFormed = IndexTransformer.handleRemove(List(index), op.index, op.value.length)(0)
    Some(setReference.copy(value = xFormed))
  }
}
