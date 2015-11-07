package com.convergencelabs.server.domain.model.ot.xform

import com.convergencelabs.server.domain.model.ot.ops.ObjectAddPropertyOperation
import com.convergencelabs.server.domain.model.ot.ops.ObjectSetPropertyOperation

object ObjectAddPropertySetPropertyTF extends OperationTransformationFunction[ObjectAddPropertyOperation, ObjectSetPropertyOperation] {
  def transform(s: ObjectAddPropertyOperation, c: ObjectSetPropertyOperation): (ObjectAddPropertyOperation, ObjectSetPropertyOperation) = {
    if (s.property == c.property) {
      throw new IllegalArgumentException("Add property and set property can not target the same property")
    } else {
      (s, c)
    }
  }
}