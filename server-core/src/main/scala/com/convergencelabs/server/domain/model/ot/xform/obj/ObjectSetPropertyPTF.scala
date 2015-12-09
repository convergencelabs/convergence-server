package com.convergencelabs.server.domain.model.ot

private[ot] object ObjectSetPropertyPTF extends PathTransformationFunction[ObjectSetPropertyOperation] {
  def transformDescendantPath(ancestor: ObjectSetPropertyOperation, descendantPath: List[_]): PathTrasformation = {
    val ancestorPathLength = ancestor.path.length;
    val commonProperty = descendantPath(ancestorPathLength).asInstanceOf[Int]

    if (ancestor.property == commonProperty) {
      PathObsoleted
    } else {
      NoPathTransformation
    }
  }
}
