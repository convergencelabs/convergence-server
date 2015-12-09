package com.convergencelabs.server.domain.model.ot

private[ot] object StringInsertInsertTF extends OperationTransformationFunction[StringInsertOperation, StringInsertOperation] {
  def transform(s: StringInsertOperation, u: StringInsertOperation): (StringOperation, StringOperation) = {
    if (s.index <= u.index) {
      (s, u.copy(index = u.index + s.value.length))
    } else {
      (s.copy(index = s.index + u.value.length), u)
    }
  }
}
