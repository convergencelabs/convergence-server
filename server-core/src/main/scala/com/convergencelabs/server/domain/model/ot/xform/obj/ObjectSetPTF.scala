package com.convergencelabs.server.domain.model.ot

private[ot] object ObjectSetPTF extends PathTransformationFunction[ObjectSetOperation] {
  def transformDescendantPath(ancestor: ObjectSetOperation, descendantPath: List[_]): PathTrasformation = {
      PathObsoleted
  }
}
