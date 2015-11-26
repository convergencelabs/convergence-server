package com.convergencelabs.server.domain.model.ot

private[ot] object StringRemoveInsertTF extends OperationTransformationFunction[StringRemoveOperation, StringInsertOperation] {
  def transform(s: StringRemoveOperation, c: StringInsertOperation): (StringOperation, StringOperation) = {
    if (c.index <= s.index) {
      (s.copy(index = s.index + c.value.length), c)
    } else if (s.index + s.value.length <= c.index) {
      (s, c.copy(index = c.index - s.value.length))
    } else {
      val offsetDelta = c.index - s.index
      (s.copy(value = s.value.substring(0, offsetDelta) + c.value + s.value.substring(offsetDelta, s.value.length)), c.copy(noOp = true))
    }
  }
}