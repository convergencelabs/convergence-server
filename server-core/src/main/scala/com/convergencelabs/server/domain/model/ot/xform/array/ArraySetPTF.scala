package com.convergencelabs.server.domain.model.ot

private[ot] object ArraySetPTF extends PathTransformationFunction[ArraySetOperation] {
  def transformDescendantPath(ancestor: ArraySetOperation, descendantPath: List[_]): PathTransformation = {
      PathObsoleted
  }
}
