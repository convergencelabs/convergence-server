package com.convergencelabs.server.domain.model.ot

import org.json4s.JsonAST.JObject
import org.scalatest.Finders
import org.scalatest.Matchers
import org.scalatest.WordSpec

class ObjectSetSetPropertyTFSpec extends WordSpec with Matchers {
  val A = "A"

  "A ObjectSetSetPropertyTF" when {

    "tranforming a set and a set property operation " must {

      /**
       * O-ST-1
       */
      "noOp the set property and not transform the set" in {
        val s = ObjectSetOperation(List(), false, JObject())
        val c = ObjectSetPropertyOperation(List(), false, A, JObject())

        val (s1, c1) = ObjectSetSetPropertyTF.transform(s, c)

        s1 shouldBe s
        c1 shouldBe ObjectSetPropertyOperation(List(), true, A, JObject())
      }
    }
  }
}
