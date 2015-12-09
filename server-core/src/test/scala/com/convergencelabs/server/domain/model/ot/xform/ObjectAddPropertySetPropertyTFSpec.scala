package com.convergencelabs.server.domain.model.ot

import org.scalatest.WordSpec
import org.json4s.JsonAST.JObject
import org.scalatest.Matchers
import org.scalatest.Finders
import org.json4s.JsonAST.JString

class ObjectAddPropertySetPropertyTFSpec extends WordSpec with Matchers {

  "A ObjectAddPropertySetPropertyTF" when {

    "tranforming an add and a set operation " must {
      "throw an exception if the property names are equal" in {
        val s = ObjectAddPropertyOperation(List(), false, "prop", JObject())
        val c = ObjectSetPropertyOperation(List(), false, "prop", JObject())

        intercept[IllegalArgumentException] {
          val (s1, c1) = ObjectAddPropertySetPropertyTF.transform(s, c)
        }
      }

      "do not transform the operations if the properties are unequal" in {
        val s = ObjectAddPropertyOperation(List(), false, "prop1", JObject())
        val c = ObjectSetPropertyOperation(List(), false, "prop2", JObject())

        val (s1, c1) = ObjectAddPropertySetPropertyTF.transform(s, c)

        s1 shouldBe s
        c1 shouldBe c
      }
    }
  }
}
