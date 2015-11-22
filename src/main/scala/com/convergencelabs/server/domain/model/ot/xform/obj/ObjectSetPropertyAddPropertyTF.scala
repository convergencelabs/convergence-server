package com.convergencelabs.server.domain.model.ot

private[ot] object ObjectSetPropertyAddPropertyTF extends OperationTransformationFunction[ObjectSetPropertyOperation, ObjectAddPropertyOperation] {
  def transform(s: ObjectSetPropertyOperation, c: ObjectAddPropertyOperation): (ObjectSetPropertyOperation, ObjectAddPropertyOperation) = {
    if (s.property == c.property) {
      throw new IllegalArgumentException("Set property and add property can not target the same property")
    } else {
      (s, c)
    }
  }
}