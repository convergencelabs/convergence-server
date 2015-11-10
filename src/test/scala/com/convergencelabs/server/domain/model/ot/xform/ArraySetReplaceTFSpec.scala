package com.convergencelabs.server.domain.model.ot.xform

import org.scalatest.Matchers
import org.scalatest.WordSpec
import com.convergencelabs.server.domain.model.ot.ops.ArrayReplaceOperation
import com.convergencelabs.server.domain.model.ot.ops.ArraySetOperation
import org.json4s.JsonAST.JArray
import org.json4s.JsonAST.JString

class ArraySetReplaceTFSpec extends WordSpec with Matchers {

  val Path = List(1, 2)
  val ClientVal = JString("x")
  val ServerVal = JString("y")

  "A ArraySetReplaceTF" when {
    "tranforming an array set against an array replace" must {

      "noOp the client's replace operation and not transform the server's set operation" in {
        val s = ArraySetOperation(Path, false, JArray(List()))
        val c = ArrayReplaceOperation(Path, false, 2, ClientVal)

        val (s1, c1) = ArraySetReplaceTF.transform(s, c)

        s1 shouldBe s
        c1 shouldBe ArrayReplaceOperation(Path, true, 2, ClientVal)
      }
    }
  }
}