package com.convergencelabs.server.domain.model.ot

import scala.math.BigInt.int2bigInt
import org.json4s.JsonAST.JInt
import org.scalatest.Finders
import org.scalatest.WordSpec

class ArrayReplacePTFSpec extends WordSpec {

  "A ArrayReplacePTF" when {
    "tranforming a descendant path" must {
      "obsolete the path if the replace is at the common path point." in {
        val ancestor = ArrayReplaceOperation(List(1, 1), false, 1, JInt(1))
        val path = List(1, 1, 1, 1)
        val result = ArrayReplacePTF.transformDescendantPath(ancestor, path)
        assert(result == PathObsoleted)
      }

      "no not trasform a path when the replace is not equal to the common path point" in {
        val ancestor = ArrayReplaceOperation(List(1, 1), false, 2, JInt(1))
        val path = List(1, 1, 1, 1)
        val result = ArrayReplacePTF.transformDescendantPath(ancestor, path)
        assert(result == NoPathTransformation)
      }
    }
  }
}
