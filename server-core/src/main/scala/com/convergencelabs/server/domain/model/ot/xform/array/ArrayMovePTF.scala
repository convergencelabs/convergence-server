package com.convergencelabs.server.domain.model.ot

private[ot] object ArrayMovePTF extends PathTransformationFunction[ArrayMoveOperation] {
  def transformDescendantPath(ancestor: ArrayMoveOperation, descendantPath: List[_]): PathTrasformation = {
    val ancestorPathLength = ancestor.path.length
    val descendantArrayIndex = descendantPath(ancestorPathLength).asInstanceOf[Int]
    if (ancestor.fromIndex < descendantArrayIndex && ancestor.toIndex > descendantArrayIndex) {
      // moved from before to after.  Decrement the index
      PathUpdated(descendantPath.updated(ancestorPathLength, descendantArrayIndex - 1))
    } else if (ancestor.fromIndex > descendantArrayIndex && ancestor.toIndex <= descendantArrayIndex) {
      // moved from after to before (or at) the index.  Increment the index.
      PathUpdated(descendantPath.updated(ancestorPathLength, descendantArrayIndex + 1))
    } else if (ancestor.fromIndex == descendantArrayIndex) {
      // The descendant path is being moved.
      PathUpdated(descendantPath.updated(ancestorPathLength, ancestor.toIndex))
    } else {
      return NoPathTranslation
    }
  }
}