package com.convergencelabs.server.domain.model.ot

import org.scalatest.WordSpec
import org.json4s.JsonAST.JObject
import org.scalatest.Matchers
import org.scalatest.Finders
import org.json4s.JsonAST.JString

class ObjectSetAddPropertyTFSpec extends WordSpec with Matchers {

  "A ObjectSetAddPropertyTF" when {

    "tranforming a set and an add property operation " must {
      "noOp the add property and not transform the set" in {
        val s = ObjectSetOperation(List(), false, JObject())
        val c = ObjectAddPropertyOperation(List(), false, "prop", JObject())
        
        val (s1, c1) = ObjectSetAddPropertyTF.transform(s, c)

        s1 shouldBe s
        c1 shouldBe ObjectAddPropertyOperation(List(), true, "prop", JObject())
      }
    }
  }
}