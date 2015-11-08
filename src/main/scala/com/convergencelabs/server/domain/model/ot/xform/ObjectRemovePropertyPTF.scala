package com.convergencelabs.server.domain.model.ot.xform

import com.convergencelabs.server.domain.model.ot.ops.ObjectRemovePropertyOperation

object ObjectRemovePropertyPTF extends PathTransformationFunction[ObjectRemovePropertyOperation] {
  def transformDescendantPath(ancestor: ObjectRemovePropertyOperation, descendantPath: List[_]): PathTrasformation = {
    val ancestorPathLength = ancestor.path.length;
    val commonProperty = descendantPath(ancestorPathLength).asInstanceOf[Int]

    if (ancestor.property == commonProperty) {
      PathObsoleted
    } else {
      NoPathTranslation
    }
  }
}