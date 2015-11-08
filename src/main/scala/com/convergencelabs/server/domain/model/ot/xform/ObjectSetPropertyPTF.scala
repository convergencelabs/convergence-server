package com.convergencelabs.server.domain.model.ot.xform

import com.convergencelabs.server.domain.model.ot.ops.ObjectSetPropertyOperation

object ObjectSetPropertyPTF extends PathTransformationFunction[ObjectSetPropertyOperation] {
  def transformDescendantPath(ancestor: ObjectSetPropertyOperation, descendantPath: List[_]): PathTrasformation = {
    val ancestorPathLength = ancestor.path.length;
    val commonProperty = descendantPath(ancestorPathLength).asInstanceOf[Int]

    if (ancestor.property == commonProperty) {
      PathObsoleted
    } else {
      NoPathTranslation
    }
  }
}