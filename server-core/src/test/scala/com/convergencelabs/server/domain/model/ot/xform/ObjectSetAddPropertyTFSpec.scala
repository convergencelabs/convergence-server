package com.convergencelabs.server.domain.model.ot

import org.json4s.JsonAST.JObject
import org.scalatest.Finders
import org.scalatest.Matchers
import org.scalatest.WordSpec

class ObjectSetAddPropertyTFSpec extends WordSpec with Matchers {

  val X = "X"

  "A ObjectSetAddPropertyTF" when {

    "tranforming a set and an add property operation " must {

      /**
       * O-SA-1
       */
      "noOp the add property and not transform the set" in {
        val s = ObjectSetOperation(List(), false, JObject())
        val c = ObjectAddPropertyOperation(List(), false, X, JObject())

        val (s1, c1) = ObjectSetAddPropertyTF.transform(s, c)

        s1 shouldBe s
        c1 shouldBe ObjectAddPropertyOperation(List(), true, X, JObject())
      }
    }
  }
}
