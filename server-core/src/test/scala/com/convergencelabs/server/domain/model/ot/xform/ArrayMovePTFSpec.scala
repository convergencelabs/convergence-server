package com.convergencelabs.server.domain.model.ot

import org.scalatest.Finders
import org.scalatest.Matchers
import org.scalatest.WordSpec

// scalastyle:off magic.number
class ArrayMovePTFSpec extends WordSpec with Matchers {

  "A ArrayMovePTF" when {
    "tranforming a descendant path" must {
      "decrement the common path index when moving from before to after the index" in {
        val ancestor = ArrayMoveOperation(List(1, 1), false, 0, 3)
        val path = List(1, 1, 1, 1)
        val result = ArrayMovePTF.transformDescendantPath(ancestor, path)
        result shouldBe PathUpdated(List(1, 1, 0, 1))
      }

      "increment the common path index when moving from after to before the index" in {
        val ancestor = ArrayMoveOperation(List(1, 1), false, 3, 0)
        val path = List(1, 1, 1, 1)
        val result = ArrayMovePTF.transformDescendantPath(ancestor, path)
        result shouldBe PathUpdated(List(1, 1, 2, 1))
      }

      "increment the common path index when moving from after to the index" in {
        val ancestor = ArrayMoveOperation(List(1, 1), false, 3, 0)
        val path = List(1, 1, 1, 1)
        val result = ArrayMovePTF.transformDescendantPath(ancestor, path)
        result shouldBe PathUpdated(List(1, 1, 2, 1))
      }

      "move the index to the to of the move when the from of the move is the index" in {
        val ancestor = ArrayMoveOperation(List(1, 1), false, 1, 4)
        val path = List(1, 1, 1, 1)
        val result = ArrayMovePTF.transformDescendantPath(ancestor, path)
        result shouldBe PathUpdated(List(1, 1, 4, 1))
      }

      "no transform if the index is entirely before the range" in {
        val ancestor = ArrayMoveOperation(List(1, 1), false, 2, 4)
        val path = List(1, 1, 1, 1)
        val result = ArrayMovePTF.transformDescendantPath(ancestor, path)
        result shouldBe NoPathTransformation
      }

      "no transform if the index is entirely after the range" in {
        val ancestor = ArrayMoveOperation(List(1, 1), false, 0, 2)
        val path = List(1, 1, 3, 1)
        val result = ArrayMovePTF.transformDescendantPath(ancestor, path)
        result shouldBe NoPathTransformation
      }
    }
  }
}
