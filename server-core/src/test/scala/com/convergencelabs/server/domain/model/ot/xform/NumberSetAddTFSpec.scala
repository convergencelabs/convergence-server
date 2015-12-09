package com.convergencelabs.server.domain.model.ot

import scala.math.BigInt.int2bigInt

import org.json4s.JsonAST.JDouble
import org.json4s.JsonAST.JInt
import org.scalatest.Finders
import org.scalatest.Matchers
import org.scalatest.WordSpec

class NumberSetAddTFSpec extends WordSpec with Matchers {

  "A NumberSetAddTF" when {

    "tranforming a set and an add operation " must {
      "noOp the client's add operation and not transform the server's set operation" in {
        val s = NumberSetOperation(List(), false, JInt(3))
        val c = NumberAddOperation(List(), false, JDouble(3D))

        val (s1, c1) = NumberSetAddTF.transform(s, c)

        s1 shouldBe s
        c1 shouldBe NumberAddOperation(List(), true, JDouble(3D))
      }
    }
  }
}
