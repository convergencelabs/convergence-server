package com.convergencelabs.server.domain.model.ot.cc.xform


import org.scalatest.WordSpec
import org.json4s.JsonAST.JArray
import org.json4s.JsonAST.JInt
import com.convergencelabs.server.domain.model.ot.ops.ArraySetOperation
import com.convergencelabs.server.domain.model.ot.xform.ArraySetPTF
import com.convergencelabs.server.domain.model.ot.xform.PathObsoleted

class ArraySetPTFSpec extends WordSpec {

  "A ArraySetPTF" when {
    "tranforming a descendant path" must {
      "obsolete the path of the descendand." in {
        val ancestor = ArraySetOperation(List(1, 1), false, JArray(List(JInt(1))))
        val path = List(1, 1, 2, 1)
        val result = ArraySetPTF.transformDescendantPath(ancestor, path)
        assert(result == PathObsoleted)
      }
    }
  }
}