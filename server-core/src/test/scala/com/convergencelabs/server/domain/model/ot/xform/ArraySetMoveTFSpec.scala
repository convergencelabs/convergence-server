package com.convergencelabs.server.domain.model.ot

import org.scalatest.Matchers
import org.scalatest.WordSpec
import org.json4s.JsonAST.JArray
import org.json4s.JsonAST.JString

// scalastyle:off magic.number
class ArraySetMoveTFSpec extends WordSpec with Matchers {

  val Path = List(1, 2)

  "A ArraySetMoveTF" when {
    "tranforming an array set against an array move" must {

      /**
       * A-SM-1
       */
      "noOp the client's move operation and not transform the server's set operation" in {
        val s = ArraySetOperation(Path, false, JArray(List(JString("X"))))
        val c = ArrayMoveOperation(Path, false, 3, 5)

        val (s1, c1) = ArraySetMoveTF.transform(s, c)

        s1 shouldBe s
        c1 shouldBe ArrayMoveOperation(Path, true, 3, 5)
      }
    }
  }
}
