package com.convergencelabs.server.domain.model.ot

private[ot] object ArrayReplacePTF extends PathTransformationFunction[ArrayReplaceOperation] {
  def transformDescendantPath(ancestor: ArrayReplaceOperation, descendantPath: List[_]): PathTrasformation = {
    val ancestorPathLength = ancestor.path.length
    val descendantArrayIndex = descendantPath(ancestorPathLength).asInstanceOf[Int]

    if (ancestor.index == descendantArrayIndex) {
      PathObsoleted
    } else {
      NoPathTransformation
    }
  }
}
