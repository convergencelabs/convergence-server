package com.convergencelabs.server.domain.model.ot.xform

import org.scalatest.Matchers
import org.scalatest.WordSpec
import com.convergencelabs.server.domain.model.ot.ops.ArraySetOperation
import com.convergencelabs.server.domain.model.ot.ops.ArraySetOperation
import org.json4s.JsonAST._

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