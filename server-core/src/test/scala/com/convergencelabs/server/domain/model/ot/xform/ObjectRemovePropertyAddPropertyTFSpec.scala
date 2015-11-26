package com.convergencelabs.server.domain.model.ot

import org.scalatest.WordSpec
import org.json4s.JsonAST.JObject
import org.scalatest.Matchers
import org.scalatest.Finders
import org.json4s.JsonAST.JString

class ObjectRemovePropertyAddPropertyTFSpec extends WordSpec with Matchers {

  "A ObjectRemovePropertyAddPropertyTF" when {

    "tranforming a set and an add operation " must {
      "throw an exception if the property names are equal" in {
        val s = ObjectRemovePropertyOperation(List(), false, "prop")
        val c = ObjectAddPropertyOperation(List(), false, "prop", JObject())

        intercept[IllegalArgumentException] {
          val (s1, c1) = ObjectRemovePropertyAddPropertyTF.transform(s, c)
        }
      }
      
      "do not transform the operations if the properties are unequal" in {
        val s = ObjectRemovePropertyOperation(List(), false, "prop1")
        val c = ObjectAddPropertyOperation(List(), false, "prop2", JObject())

        val (s1, c1) = ObjectRemovePropertyAddPropertyTF.transform(s, c)
        
        s1 shouldBe s
        c1 shouldBe c
      }
    }
  }
}