package com.convergencelabs.server.domain.model.ot

private[ot] object ObjectAddPropertyRemovePropertyTF extends OperationTransformationFunction[ObjectAddPropertyOperation, ObjectRemovePropertyOperation] {
  def transform(s: ObjectAddPropertyOperation, c: ObjectRemovePropertyOperation): (ObjectAddPropertyOperation, ObjectRemovePropertyOperation) = {
    if (s.property != c.property) {
      // O-AR-1
      (s, c)
    } else {
      // O-AR-2
      throw new IllegalArgumentException("Add property and remove property can not target the same property")
    }
  }
}
