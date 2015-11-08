package com.convergencelabs.server.domain.model.ot.cc.xform

import scala.math.BigInt.int2bigInt
import org.json4s.JsonAST.JInt
import org.scalatest.Finders
import org.scalatest.WordSpec
import com.convergencelabs.server.domain.model.ot.ops.ArrayRemoveOperation
import com.convergencelabs.server.domain.model.ot.xform.ArrayRemovePTF
import com.convergencelabs.server.domain.model.ot.xform.PathUpdated
import com.convergencelabs.server.domain.model.ot.xform.NoPathTranslation
import com.convergencelabs.server.domain.model.ot.xform.PathObsoleted

class ArrayRemovePTFSpec extends WordSpec {

  "A ArrayRemovePTF" when {
    "tranforming a descendant path" must {
      "decrement an earlier path by one at the common path point." in {
        val ancestor = ArrayRemoveOperation(List(1, 1), false, 0)
        val path = List(1, 1, 1, 1)
        val result = ArrayRemovePTF.transformDescendantPath(ancestor, path)
        assert(result == PathUpdated(List(1, 1, 0, 1)))
      }
      
      "obsolete the path if the remove is at the common path point." in {
        val ancestor = ArrayRemoveOperation(List(1, 1), false, 1)
        val path = List(1, 1, 1, 1)
        val result = ArrayRemovePTF.transformDescendantPath(ancestor, path)
        assert(result == PathObsoleted)
      }
      
      "no not trasform a path when the insert is after the common path" in {
        val ancestor = ArrayRemoveOperation(List(1, 1), false, 2)
        val path = List(1, 1, 1, 1)
        val result = ArrayRemovePTF.transformDescendantPath(ancestor, path)
        assert(result == NoPathTranslation)
      }
    }
  }
}