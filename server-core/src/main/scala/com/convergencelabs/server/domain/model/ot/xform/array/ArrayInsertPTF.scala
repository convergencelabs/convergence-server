package com.convergencelabs.server.domain.model.ot

private[ot] object ArrayInsertPTF extends PathTransformationFunction[ArrayInsertOperation] {
  def transformDescendantPath(ancestor: ArrayInsertOperation, descendantPath: List[_]): PathTrasformation = {
    val ancestorPathLength = ancestor.path.length
    val descendantPathIndex = descendantPath(ancestorPathLength).asInstanceOf[Int]
    if (ancestor.index <= descendantPathIndex) {
      PathUpdated(descendantPath.updated(ancestorPathLength, descendantPathIndex + 1))
    } else {
      NoPathTransformation
    }
  }
}
