package com.convergencelabs.server.domain.model.ot

import org.scalatest.Finders
import org.scalatest.WordSpec
import org.scalatest.Matchers
import org.json4s.JsonAST.JInt

class ObjectSetPropertyPTFSpec extends WordSpec with Matchers {

  val property = "prop"

  "A ObjectSetPropertyPTF" when {
    "tranforming a descendant path" must {

      "obsolete the path if the property matches the common path pont" in {
        val ancestor = ObjectSetPropertyOperation(List(1, 1), false, property, JInt(1))
        val path = List(1, 1, property, 1)
        val result = ObjectSetPropertyPTF.transformDescendantPath(ancestor, path)
        result shouldBe PathObsoleted
      }

      "not trasform a path when the properties are different at the common point" in {
        val ancestor = ObjectSetPropertyOperation(List(1, 1), false, "prop2", JInt(1))
        val path = List(1, 1, property, 1)
        val result = ObjectSetPropertyPTF.transformDescendantPath(ancestor, path)
        result shouldBe NoPathTransformation
      }
    }
  }
}
