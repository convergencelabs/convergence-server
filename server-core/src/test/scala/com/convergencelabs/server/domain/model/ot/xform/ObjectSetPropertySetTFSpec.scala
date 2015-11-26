package com.convergencelabs.server.domain.model.ot

import org.json4s.JsonAST.JObject
import org.scalatest.Finders
import org.scalatest.Matchers
import org.scalatest.WordSpec

class ObjectSetPropertySetTFSpec extends WordSpec with Matchers {

  "A ObjectSetPropertySetTF" when {

    "tranforming an set property and a set operation " must {
      "noOp the set property and not transform the set" in {
        val s = ObjectSetPropertyOperation(List(), false, "prop", JObject())
        val c = ObjectSetOperation(List(), false, JObject())
        
        val (s1, c1) = ObjectSetPropertySetTF.transform(s, c)

        s1 shouldBe ObjectSetPropertyOperation(List(), true, "prop", JObject())
        c1 shouldBe c
      }
    }
  }
}