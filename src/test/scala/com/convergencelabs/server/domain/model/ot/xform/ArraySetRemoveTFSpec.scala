package com.convergencelabs.server.domain.model.ot.xform

import org.scalatest.Matchers
import org.scalatest.WordSpec
import com.convergencelabs.server.domain.model.ot.ops.ArrayRemoveOperation
import com.convergencelabs.server.domain.model.ot.ops.ArraySetOperation
import org.json4s.JsonAST.JArray
import org.json4s.JsonAST.JString

class ArraySetRemoveTFSpec extends WordSpec with Matchers {

  val Path = List(1, 2)
  val ClientVal = JString("x")
  val ServerVal = JString("y")

  "A ArraySetRemoveTF" when {
    "tranforming an array set against an array remove" must {

      "noOp the client's remove operation and not transform the server's set operation" in {
        val s = ArraySetOperation(Path, false, JArray(List()))
        val c = ArrayRemoveOperation(Path, false, 2)

        val (s1, c1) = ArraySetRemoveTF.transform(s, c)

        s1 shouldBe s
        c1 shouldBe ArrayRemoveOperation(Path, true, 2)
      }
    }
  }
}