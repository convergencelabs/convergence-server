package com.convergencelabs.server.domain.model.ot

private[ot] object ObjectAddPropertyAddPropertyTF extends OperationTransformationFunction[ObjectAddPropertyOperation, ObjectAddPropertyOperation] {
  def transform(s: ObjectAddPropertyOperation, c: ObjectAddPropertyOperation): (ObjectOperation, ObjectAddPropertyOperation) = {
    if (s.property == c.property) {
      if (s.newValue == c.newValue) {
        // Same property and value, noOp them both.
        (s.copy(noOp = true), c.copy(noOp = true))
      } else {
        // The server and client set the same property to different values.
        // The server wins, so the client's operation is noOp'ed and the
        // servers operation is transformed into a set property.
        val ObjectAddPropertyOperation(path, noOp, prop, value) = s
        (ObjectSetPropertyOperation(path, noOp, prop, value), c.copy(noOp = true))
      }
    } else {
      // Different properties are being added, so they do not conflict.
      (s, c)
    }
  }
}