package com.convergencelabs.server.domain.model.ot

import org.json4s.JsonAST.JObject
import org.scalatest.Matchers
import org.scalatest.WordSpec

class ObjectRemovePropertySetTFSpec extends WordSpec with Matchers {

  val A = "A"

  "A ObjectRemovePropertySetTF" when {
    "tranforming a remove and a set operation " must {

      /**
       * O-RS-1
       */
      "transform the set into an add, noOp the remove" in {
        val s = ObjectRemovePropertyOperation(List(), false, A)
        val c = ObjectSetOperation(List(), false, JObject())

        val (s1, c1) = ObjectRemovePropertySetTF.transform(s, c)

        s1 shouldBe ObjectRemovePropertyOperation(List(), true, A)
        c1 shouldBe ObjectSetOperation(List(), false, JObject())
      }
    }
  }
}
