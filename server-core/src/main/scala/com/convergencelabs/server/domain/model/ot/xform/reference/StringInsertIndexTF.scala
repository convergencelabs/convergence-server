package com.convergencelabs.server.domain.model.ot.xform.reference

import com.convergencelabs.server.domain.model.ot.ReferenceTransformationFunction
import com.convergencelabs.server.domain.model.ot.StringInsertOperation
import com.convergencelabs.server.domain.model.SetReference
import com.convergencelabs.server.domain.model.ot.xform.IndexTransformer

object StringInsertIndexTF extends ReferenceTransformationFunction[StringInsertOperation] {
  def transform(op: StringInsertOperation, setReference: SetReference): SetReference = {
    val index = setReference.value.asInstanceOf[Int]
    val xFormed = IndexTransformer.handleInsert(List(index), op.index, op.value.length)(0)
    setReference.copy(value = xFormed)
  }
}