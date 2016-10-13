package com.convergencelabs.server.domain.model.ot

private[ot] object ObjectAddPropertySetPropertyTF extends OperationTransformationFunction[ObjectAddPropertyOperation, ObjectSetPropertyOperation] {
  def transform(s: ObjectAddPropertyOperation, c: ObjectSetPropertyOperation): (ObjectAddPropertyOperation, ObjectSetPropertyOperation) = {
    if (s.property != c.property) {
      // O-AT-1
      (s, c)
    } else {
      // O-AT-2
      throw new IllegalArgumentException("Add property and set property can not target the same property")
    }
  }
}
