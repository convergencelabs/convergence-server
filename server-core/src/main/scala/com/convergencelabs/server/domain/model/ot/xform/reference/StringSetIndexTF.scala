package com.convergencelabs.server.domain.model.ot.xform.reference

import com.convergencelabs.server.domain.model.SetReference
import com.convergencelabs.server.domain.model.ot.ReferenceTransformationFunction
import com.convergencelabs.server.domain.model.ot.StringRemoveOperation
import com.convergencelabs.server.domain.model.ot.StringSetOperation

object StringSetIndexTF extends ReferenceTransformationFunction[StringSetOperation] {
  def transform(op: StringSetOperation, setReference: SetReference): Option[SetReference] = {
    None
  }
}
