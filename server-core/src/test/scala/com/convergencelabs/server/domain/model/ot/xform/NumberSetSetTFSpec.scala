package com.convergencelabs.server.domain.model.ot

import scala.math.BigInt.int2bigInt

import org.json4s.JsonAST.JInt
import org.scalatest.Finders
import org.scalatest.Matchers
import org.scalatest.WordSpec

// scalastyle:off magic.number
class NumberSetSetTFSpec extends WordSpec with Matchers {

  "A NumberSetSetTF" when {

    "tranforming a set and set operation " must {
      /**
       * N-SS-1
       */
      "noOp the client's set if the two operations are not identical" in {
        val s = NumberSetOperation(List(), false, JInt(1))
        val c = NumberSetOperation(List(), false, JInt(2))

        val (s1, c1) = NumberSetSetTF.transform(s, c)

        s1 shouldBe NumberSetOperation(List(), false, JInt(1))
        c1 shouldBe NumberSetOperation(List(), true, JInt(2))
      }

      /**
       * N-SS-2
       */
      "noOp both operations if they are identical" in {
        val s = NumberSetOperation(List(), false, JInt(1))
        val c = NumberSetOperation(List(), false, JInt(1))

        val (s1, c1) = NumberSetSetTF.transform(s, c)

        s1 shouldBe NumberSetOperation(List(), true, JInt(1))
        c1 shouldBe NumberSetOperation(List(), true, JInt(1))
      }
    }
  }
}
