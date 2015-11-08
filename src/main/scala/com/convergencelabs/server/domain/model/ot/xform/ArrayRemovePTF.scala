package com.convergencelabs.server.domain.model.ot.xform

import com.convergencelabs.server.domain.model.ot.ops.ArrayRemoveOperation

object ArrayRemovePTF extends PathTransformationFunction[ArrayRemoveOperation] {
  def transformDescendantPath(ancestor: ArrayRemoveOperation, descendantPath: List[_]): PathTrasformation = {
    val ancestorPathLength = ancestor.path.length
    val descendantArrayIndex = descendantPath(ancestorPathLength).asInstanceOf[Int]

    if (ancestor.index < descendantArrayIndex) {
      PathUpdated(descendantPath.updated(ancestorPathLength, descendantArrayIndex - 1))
    } else if (ancestor.index == descendantArrayIndex) {
      PathObsoleted
    } else {
      NoPathTranslation
    }
  }
}