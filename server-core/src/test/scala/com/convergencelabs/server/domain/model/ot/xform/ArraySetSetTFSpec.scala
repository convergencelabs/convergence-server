package com.convergencelabs.server.domain.model.ot

import scala.math.BigInt.int2bigInt

import org.json4s.JsonAST.JArray
import org.json4s.JsonAST.JInt
import org.json4s.JsonAST.JString
import org.scalatest.Finders
import org.scalatest.Matchers
import org.scalatest.WordSpec

class ArraySetSetTFSpec extends WordSpec with Matchers {

  val Path = List(1, 2)
  val X = JString("X")
  val Y = JString("Y")

  "A ArraySetSetTF" when {
    "tranforming an array set against an array set" must {
      /**
       * A-SS-1
       */
      "noOp the client's set operation and not transform the server's set operation, when the values are not equal" in {
        val s = ArraySetOperation(Path, false, JArray(List(X)))
        val c = ArraySetOperation(Path, false, JArray(List(Y)))

        val (s1, c1) = ArraySetSetTF.transform(s, c)

        s1 shouldBe s
        c1 shouldBe ArraySetOperation(Path, true, JArray(List(Y)))
      }

      /**
       * A-SS-2
       */
      "noOp both operations, when the values are  equal" in {
        val s = ArraySetOperation(Path, false, JArray(List(X)))
        val c = ArraySetOperation(Path, false, JArray(List(X)))

        val (s1, c1) = ArraySetSetTF.transform(s, c)

        s1 shouldBe ArraySetOperation(Path, true, JArray(List(X)))
        c1 shouldBe ArraySetOperation(Path, true, JArray(List(X)))
      }
    }
  }
}
