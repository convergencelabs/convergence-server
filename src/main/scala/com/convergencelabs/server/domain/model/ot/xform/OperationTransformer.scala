package com.convergencelabs.server.domain.model.ot.xform

import com.convergencelabs.server.domain.model.ot.ops.Operation

class OperationTransformer {
  def transform(processedOperation: Operation, incomingOperation: Operation): (Operation, Operation) = {
    (processedOperation, incomingOperation)
  }
}
