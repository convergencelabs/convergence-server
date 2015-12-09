package com.convergencelabs.server.domain.model.ot

import org.json4s.JsonAST.JObject
import org.scalatest.Finders
import org.scalatest.Matchers
import org.scalatest.WordSpec

class ObjectAddPropertyRemovePropertyTFSpec extends WordSpec with Matchers {

  "A ObjectAddPropertyRemovePropertyTF" when {

    "tranforming an add and a set operation " must {
      "throw an exception if the property names are equal" in {
        val s = ObjectAddPropertyOperation(List(), false, "prop", JObject())
        val c = ObjectRemovePropertyOperation(List(), false, "prop")

        intercept[IllegalArgumentException] {
          val (s1, c1) = ObjectAddPropertyRemovePropertyTF.transform(s, c)
        }
      }

      "do not transform the operations if the properties are unequal" in {
        val s = ObjectAddPropertyOperation(List(), false, "prop1", JObject())
        val c = ObjectRemovePropertyOperation(List(), false, "prop2")

        val (s1, c1) = ObjectAddPropertyRemovePropertyTF.transform(s, c)

        s1 shouldBe s
        c1 shouldBe c
      }
    }
  }
}
