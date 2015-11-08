package com.convergencelabs.server.domain.model.ot.xform

import com.convergencelabs.server.domain.model.ot.ops.ArraySetOperation

object ArraySetPTF extends PathTransformationFunction[ArraySetOperation] {
  def transformDescendantPath(ancestor: ArraySetOperation, descendantPath: List[_]): PathTrasformation = {
      PathObsoleted
  }
}