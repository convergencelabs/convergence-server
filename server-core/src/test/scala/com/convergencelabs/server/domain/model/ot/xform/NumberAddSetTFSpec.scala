package com.convergencelabs.server.domain.model.ot

import scala.math.BigInt.int2bigInt

import org.json4s.JsonAST.JDouble
import org.json4s.JsonAST.JInt
import org.scalatest.Finders
import org.scalatest.Matchers
import org.scalatest.WordSpec

// scalastyle:off magic.number
class NumberAddSetTFSpec extends WordSpec with Matchers {

  "A NumberAddSetTF" when {

    /**
     * N-AS-1
     */
    "tranforming an add and an set operation " must {
      "noOp the server's add operation and not transform the client's set operation" in {
        val s = NumberAddOperation(List(), false, JDouble(1))
        val c = NumberSetOperation(List(), false, JDouble(2))

        val (s1, c1) = NumberAddSetTF.transform(s, c)

        s1 shouldBe NumberAddOperation(List(), true, JDouble(1))
        c1 shouldBe c
      }
    }
  }
}
