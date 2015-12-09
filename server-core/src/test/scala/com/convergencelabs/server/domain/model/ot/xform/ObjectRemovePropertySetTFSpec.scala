package com.convergencelabs.server.domain.model.ot

import org.scalatest.WordSpec
import org.json4s.JsonAST.JObject
import org.scalatest.Matchers
import org.scalatest.Finders
import org.json4s.JsonAST.JString

class ObjectRemovePropertySetTFSpec extends WordSpec with Matchers {

  "A ObjectRemovePropertySetTF" when {
    "tranforming a remove and a set operation " must {
      "transform the set into an add, noOp the remove" in {
        val s = ObjectRemovePropertyOperation(List(), false, "prop")
        val c = ObjectSetOperation(List(), false, JObject())

        val (s1, c1) = ObjectRemovePropertySetTF.transform(s, c)

        s1 shouldBe ObjectRemovePropertyOperation(List(), true, "prop")
        c1 shouldBe ObjectSetOperation(List(), false, JObject())
      }
    }
  }
}
