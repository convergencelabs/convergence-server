package com.convergencelabs.server.domain.model.ot

import org.scalatest.Matchers
import org.scalatest.WordSpec
import org.json4s.JsonAST.JArray
import org.json4s.JsonAST.JString

class ArraySetMoveTFSpec extends WordSpec with Matchers {

  val Path = List(1, 2)

  "A ArraySetMoveTF" when {
    "tranforming an array set against an array move" must {

      "noOp the client's move operation and not transform the server's set operation" in {
        val s = ArraySetOperation(Path, false, JArray(List()))
        val c = ArrayMoveOperation(Path, false, 2, 3)

        val (s1, c1) = ArraySetMoveTF.transform(s, c)

        s1 shouldBe s
        c1 shouldBe ArrayMoveOperation(Path, true, 2, 3)
      }
    }
  }
}
