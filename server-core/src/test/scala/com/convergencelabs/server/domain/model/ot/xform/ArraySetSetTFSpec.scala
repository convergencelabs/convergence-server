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
  val ClientVal = JString("x")
  val ServerVal = JString("y")

  "A ArraySetSetTF" when {
    "tranforming an array set against an array set" must {
      "noOp the client's set operation and not transform the server's set operation" in {
        val s = ArraySetOperation(Path, false, JArray(List(JInt(1))))
        val c = ArraySetOperation(Path, false, JArray(List(JInt(2))))

        val (s1, c1) = ArraySetSetTF.transform(s, c)

        s1 shouldBe s
        c1 shouldBe ArraySetOperation(Path, true, JArray(List(JInt(2))))
      }
    }
  }
}
