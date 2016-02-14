package com.convergencelabs.server.domain.model.ot

import org.json4s.JsonAST.JArray
import org.json4s.JsonAST.JString
import org.scalatest.Finders
import org.scalatest.Matchers
import org.scalatest.WordSpec

// scalastyle:off magic.number
class ArraySetReplaceTFSpec extends WordSpec with Matchers {

  val Path = List(1, 2)
  val X = JString("X")
  val Y = JString("Y")

  "A ArraySetReplaceTF" when {
    "tranforming an array set against an array replace" must {

      /**
       * A-SP-1
       */
      "noOp the client's replace operation and not transform the server's set operation" in {
        val s = ArraySetOperation(Path, false, JArray(List(X)))
        val c = ArrayReplaceOperation(Path, false, 4, Y)

        val (s1, c1) = ArraySetReplaceTF.transform(s, c)

        s1 shouldBe s
        c1 shouldBe ArrayReplaceOperation(Path, true, 4, Y)
      }
    }
  }
}
