package com.convergencelabs.server.domain.model.ot.cc.xform

import scala.math.BigInt.int2bigInt
import org.json4s.JsonAST.JInt
import org.scalatest.Finders
import org.scalatest.WordSpec
import com.convergencelabs.server.domain.model.ot.ops.ArrayInsertOperation
import com.convergencelabs.server.domain.model.ot.xform.ArrayInsertPTF
import com.convergencelabs.server.domain.model.ot.xform.PathUpdated
import com.convergencelabs.server.domain.model.ot.xform.NoPathTranslation

class ArrayInsertPTFSpec extends WordSpec {

  "A ArrayInsertPTF" when {
    "tranforming a descendant path" must {
      "increment an earlier path by one at the common path point." in {
        val ancestor = ArrayInsertOperation(List(1, 1), false, 0, JInt(1))
        val path = List(1, 1, 1, 1)
        val result = ArrayInsertPTF.transformDescendantPath(ancestor, path)
        assert(result == PathUpdated(List(1, 1, 2, 1)))
      }
      
      "increment an equal path by one at the common path point." in {
        val ancestor = ArrayInsertOperation(List(1, 1), false, 1, JInt(1))
        val path = List(1, 1, 1, 1)
        val result = ArrayInsertPTF.transformDescendantPath(ancestor, path)
        assert(result == PathUpdated(List(1, 1, 2, 1)))
      }
      
      "no not trasform a path when the insert is after the common path" in {
        val ancestor = ArrayInsertOperation(List(1, 1), false, 2, JInt(1))
        val path = List(1, 1, 1, 1)
        val result = ArrayInsertPTF.transformDescendantPath(ancestor, path)
        assert(result == NoPathTranslation)
      }
    }
  }
}