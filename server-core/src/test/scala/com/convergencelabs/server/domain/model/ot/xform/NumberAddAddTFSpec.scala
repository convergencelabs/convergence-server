package com.convergencelabs.server.domain.model.ot

import scala.math.BigInt.int2bigInt

import org.json4s.JsonAST.JDouble
import org.json4s.JsonAST.JInt
import org.scalatest.Finders
import org.scalatest.Matchers
import org.scalatest.WordSpec

class NumberAddAddTFSpec extends WordSpec with Matchers {

  "A NumberAddAddTF" when {

    "tranforming an add and an add operation " must {
      "do not transform two number add operations" in {
        val s = NumberAddOperation(List(), false, JInt(3))
        val c = NumberAddOperation(List(), false, JDouble(3D))

        val (s1, c1) = NumberAddAddTF.transform(s, c)

        s1 shouldBe s
        c1 shouldBe c
      }
    }
  }
}
