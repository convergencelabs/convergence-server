package com.convergencelabs.server.domain.model.ot

/**
 * This transformation function handles the case where a server 
 * StringInsertOperation is concurrent with a client StringInsertOperation.
 * The major consideration in determining what transformation path to take
 * is the relative position of the two operation's positional indices.
 */
private[ot] object StringInsertInsertTF extends OperationTransformationFunction[StringInsertOperation, StringInsertOperation] {
  def transform(s: StringInsertOperation, u: StringInsertOperation): (StringOperation, StringOperation) = {
    if (s.index <= u.index) {
      // S-II-1 and S-II-2
      (s, u.copy(index = u.index + s.value.length))
    } else {
      // S-II-3
      (s.copy(index = s.index + u.value.length), u)
    }
  }
}
