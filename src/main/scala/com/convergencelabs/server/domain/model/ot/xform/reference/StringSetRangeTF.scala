package com.convergencelabs.server.domain.model.ot.xform.reference

import com.convergencelabs.server.domain.model.ot.ReferenceTransformationFunction
import com.convergencelabs.server.domain.model.ot.StringRemoveOperation
import com.convergencelabs.server.domain.model.ot.StringSetOperation
import com.convergencelabs.server.domain.model.ReferenceValue

object StringSetRangeTF extends ReferenceTransformationFunction[StringSetOperation] {
  def transform(op: StringSetOperation, setReference: ReferenceValue): Option[ReferenceValue] = {
    None
  }
}
