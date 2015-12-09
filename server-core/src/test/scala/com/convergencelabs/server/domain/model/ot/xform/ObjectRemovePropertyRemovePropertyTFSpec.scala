package com.convergencelabs.server.domain.model.ot

import org.scalatest.WordSpec
import org.json4s.JsonAST.JObject
import org.scalatest.Matchers
import org.scalatest.Finders
import org.json4s.JsonAST.JString

class ObjectRemovePropertyRemovePropertyTFSpec extends WordSpec with Matchers {

  "A ObjectRemovePropertyRemovePropertyTF" when {

    "tranforming a set and an add operation " must {
      "noOp both operations if the properties are the same" in {
        val s = ObjectRemovePropertyOperation(List(), false, "prop")
        val c = ObjectRemovePropertyOperation(List(), false, "prop")

        val (s1, c1) = ObjectRemovePropertyRemovePropertyTF.transform(s, c)

        s1 shouldBe ObjectRemovePropertyOperation(List(), true, "prop")
        c1 shouldBe ObjectRemovePropertyOperation(List(), true, "prop")
      }

      "not transform the operations if the properties are unequal" in {
        val s = ObjectRemovePropertyOperation(List(), false, "prop1")
        val c = ObjectRemovePropertyOperation(List(), false, "prop2")

        val (s1, c1) = ObjectRemovePropertyRemovePropertyTF.transform(s, c)

        s1 shouldBe s
        c1 shouldBe c
      }
    }
  }
}
