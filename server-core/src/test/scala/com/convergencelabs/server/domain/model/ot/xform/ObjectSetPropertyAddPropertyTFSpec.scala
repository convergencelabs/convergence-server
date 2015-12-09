package com.convergencelabs.server.domain.model.ot

import org.json4s.JsonAST.JObject
import org.scalatest.Finders
import org.scalatest.Matchers
import org.scalatest.WordSpec

class ObjectSetPropertyAddPropertyTFSpec extends WordSpec with Matchers {

  "A ObjectSetPropertyAddPropertyTF" when {

    "tranforming a set and an add operation " must {
      "throw an exception if the property names are equal" in {
        val s = ObjectSetPropertyOperation(List(), false, "prop", JObject())
        val c = ObjectAddPropertyOperation(List(), false, "prop", JObject())

        intercept[IllegalArgumentException] {
          val (s1, c1) = ObjectSetPropertyAddPropertyTF.transform(s, c)
        }
      }

      "do not transform the operations if the properties are unequal" in {
        val s = ObjectSetPropertyOperation(List(), false, "prop1", JObject())
        val c = ObjectAddPropertyOperation(List(), false, "prop2", JObject())

        val (s1, c1) = ObjectSetPropertyAddPropertyTF.transform(s, c)

        s1 shouldBe s
        c1 shouldBe c
      }
    }
  }
}
