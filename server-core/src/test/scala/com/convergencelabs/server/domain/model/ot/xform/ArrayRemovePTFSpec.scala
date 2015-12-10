package com.convergencelabs.server.domain.model.ot

import org.scalatest.Finders
import org.scalatest.WordSpec
import org.scalatest.Matchers

class ArrayRemovePTFSpec extends WordSpec with Matchers {

  "A ArrayRemovePTF" when {
    "tranforming a descendant path" must {
      "decrement an earlier path by one at the common path point." in {
        val ancestor = ArrayRemoveOperation(List(1, 1), false, 0)
        val path = List(1, 1, 1, 1)
        val result = ArrayRemovePTF.transformDescendantPath(ancestor, path)
        result shouldBe PathUpdated(List(1, 1, 0, 1))
      }

      "obsolete the path if the remove is at the common path point." in {
        val ancestor = ArrayRemoveOperation(List(1, 1), false, 1)
        val path = List(1, 1, 1, 1)
        val result = ArrayRemovePTF.transformDescendantPath(ancestor, path)
        result shouldBe PathObsoleted
      }

      "no not trasform a path when the insert is after the common path" in {
        val ancestor = ArrayRemoveOperation(List(1, 1), false, 2)
        val path = List(1, 1, 1, 1)
        val result = ArrayRemovePTF.transformDescendantPath(ancestor, path)
        result shouldBe NoPathTransformation
      }
    }
  }
}
