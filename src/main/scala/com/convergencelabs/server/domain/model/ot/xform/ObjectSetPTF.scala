package com.convergencelabs.server.domain.model.ot.xform

import com.convergencelabs.server.domain.model.ot.ops.ObjectSetOperation

object ObjectSetPTF extends PathTransformationFunction[ObjectSetOperation] {
  def transformDescendantPath(ancestor: ObjectSetOperation, descendantPath: List[_]): PathTrasformation = {
      PathObsoleted
  }
}