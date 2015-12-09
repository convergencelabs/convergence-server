package com.convergencelabs.server.domain.model.ot

import org.json4s.JsonAST.JArray
import org.json4s.JsonAST.JString
import org.scalatest.Finders
import org.scalatest.Matchers
import org.scalatest.WordSpec

class ArrayReplaceSetTFSpec extends WordSpec with Matchers {

  val Path = List(1, 2)
  val ClientVal = JString("x")
  val ServerVal = JString("y")

  "A ArrayReplaceSetTF" when {
    "tranforming an array replace against an array set" must {

      "noOp the server's replace operation and not transform the client's set operation" in {
        val s = ArrayReplaceOperation(Path, false, 2, ServerVal)
        val c = ArraySetOperation(Path, false, JArray(List()))

        val (s1, c1) = ArrayReplaceSetTF.transform(s, c)

        s1 shouldBe ArrayReplaceOperation(Path, true, 2, ServerVal)
        c1 shouldBe c
      }
    }
  }
}
