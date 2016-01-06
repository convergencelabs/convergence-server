package com.convergencelabs.server.domain.model.ot

import org.scalatest.Finders
import org.scalatest.Matchers
import org.scalatest.WordSpec

class BooleanSetSetTFSpec extends WordSpec with Matchers {

  "A BooleanSetSetTF" when {

    "tranforming a set and set operation " must {

      /**
       * B-SS-1
       */
      "noOp the client's set if the two operations are not identical" in {
        val s = BooleanSetOperation(List(), false, true)
        val c = BooleanSetOperation(List(), false, false)

        val (s1, c1) = BooleanSetSetTF.transform(s, c)

        s1 shouldBe s
        c1 shouldBe BooleanSetOperation(List(), true, false)
      }

      /**
       * B-SS-2
       */
      "noOp both operations if they are identical" in {
        val s = BooleanSetOperation(List(), false, true)
        val c = BooleanSetOperation(List(), false, true)

        val (s1, c1) = BooleanSetSetTF.transform(s, c)

        s1 shouldBe BooleanSetOperation(List(), true, true)
        c1 shouldBe BooleanSetOperation(List(), true, true)
      }
    }
  }
}
