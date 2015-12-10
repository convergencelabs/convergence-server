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
      "noOp both operations if they are identical" in {
        val s = NumberSetOperation(List(), false, JInt(3))
        val c = NumberSetOperation(List(), false, JInt(3))

        val (s1, c1) = NumberSetSetTF.transform(s, c)

        s1 shouldBe NumberSetOperation(List(), true, JInt(3))
        c1 shouldBe NumberSetOperation(List(), true, JInt(3))
      }

      "noOp the client's set if the two operations are not identical" in {
        val s = NumberSetOperation(List(), false, JInt(3))
        val c = NumberSetOperation(List(), false, JInt(4))

        val (s1, c1) = NumberSetSetTF.transform(s, c)

        s1 shouldBe NumberSetOperation(List(), false, JInt(3))
        c1 shouldBe NumberSetOperation(List(), true, JInt(4))
      }
    }
  }
}
