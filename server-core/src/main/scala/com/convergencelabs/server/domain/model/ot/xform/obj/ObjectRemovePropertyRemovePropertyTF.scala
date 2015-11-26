package com.convergencelabs.server.domain.model.ot

private[ot] object ObjectRemovePropertyAddPropertyTF extends OperationTransformationFunction[ObjectRemovePropertyOperation, ObjectAddPropertyOperation] {
  def transform(s: ObjectRemovePropertyOperation, c: ObjectAddPropertyOperation): (ObjectRemovePropertyOperation, ObjectAddPropertyOperation) = {
    if (s.property == c.property) {
      throw new IllegalArgumentException("Remove property and add property can not target the same property")
    } else {
      (s, c)
    }
  }
}