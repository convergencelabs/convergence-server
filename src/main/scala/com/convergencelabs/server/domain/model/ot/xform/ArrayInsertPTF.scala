package com.convergencelabs.server.domain.model.ot.xform

import com.convergencelabs.server.domain.model.ot.ops.ArrayInsertOperation

object ArrayInsertPTF extends PathTransformationFunction[ArrayInsertOperation] {
  def transformDescendantPath(ancestor: ArrayInsertOperation, descendantPath: List[_]): PathTrasformation = {
    val ancestorPathLength = ancestor.path.length
    val descendantPathIndex = descendantPath(ancestorPathLength).asInstanceOf[Int]
    if (ancestor.index <= descendantPathIndex) {
      PathUpdated(descendantPath.updated(ancestorPathLength, descendantPathIndex + 1))
    } else {
      NoPathTranslation
    }
  }
}