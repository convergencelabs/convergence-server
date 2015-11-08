package com.convergencelabs.server.domain.model.ot.xform

import com.convergencelabs.server.domain.model.ot.ops.ArrayReplaceOperation

object ArrayReplacePTF extends PathTransformationFunction[ArrayReplaceOperation] {
  def transformDescendantPath(ancestor: ArrayReplaceOperation, descendantPath: List[_]): PathTrasformation = {
    val ancestorPathLength = ancestor.path.length
    val descendantArrayIndex = descendantPath(ancestorPathLength).asInstanceOf[Int]

    if (ancestor.index == descendantArrayIndex) {
      PathObsoleted
    } else {
      NoPathTranslation
    }
  }
}