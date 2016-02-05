package com.convergencelabs.server.domain.model.ot

private[ot] object ObjectSetPropertyPTF extends PathTransformationFunction[ObjectSetPropertyOperation] {
  def transformDescendantPath(ancestor: ObjectSetPropertyOperation, descendantPath: List[_]): PathTransformation = {
    val ancestorPathLength = ancestor.path.length;
    val commonProperty = descendantPath(ancestorPathLength).asInstanceOf[String]

    if (ancestor.property == commonProperty) {
      PathObsoleted
    } else {
      NoPathTransformation
    }
  }
}
