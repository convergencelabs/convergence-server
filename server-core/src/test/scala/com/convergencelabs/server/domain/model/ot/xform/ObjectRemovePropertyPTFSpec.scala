package com.convergencelabs.server.domain.model.ot

import org.scalatest.Finders
import org.scalatest.WordSpec
import org.scalatest.Matchers

class ObjectRemovePropertyPTFSpec extends WordSpec with Matchers {

  val property = "prop" 
  
  "A ObjectRemovePropertyPTF" when {
    "tranforming a descendant path" must {

      "obsolete the path if the remove is at the common path point." in {
        val ancestor = ObjectRemovePropertyOperation(List(1, 1), false, property)
        val path = List(1, 1, property, 1)
        val result = ObjectRemovePropertyPTF.transformDescendantPath(ancestor, path)
        result shouldBe PathObsoleted
      }

      "not trasform a path when the insert is after the common path" in {
        val ancestor = ObjectRemovePropertyOperation(List(1, 1), false, "prop2")
        val path = List(1, 1, property, 1)
        val result = ObjectRemovePropertyPTF.transformDescendantPath(ancestor, path)
        result shouldBe NoPathTransformation
      }
    }
  }
}
