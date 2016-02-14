package com.convergencelabs.server.domain.model.ot

private[ot] object ObjectRemovePropertyPTF extends PathTransformationFunction[ObjectRemovePropertyOperation] {
  def transformDescendantPath(ancestor: ObjectRemovePropertyOperation, descendantPath: List[_]): PathTransformation = {
    val ancestorPathLength = ancestor.path.length;
    val commonProperty = descendantPath(ancestorPathLength).asInstanceOf[String]

    if (ancestor.property == commonProperty) {
      PathObsoleted
    } else {
      NoPathTransformation
    }
  }
}
