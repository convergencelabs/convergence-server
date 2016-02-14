package com.convergencelabs.server.domain.model.ot

import org.json4s.JsonAST.JArray
import org.json4s.JsonAST.JString
import org.scalatest.Finders
import org.scalatest.Matchers
import org.scalatest.WordSpec

// scalastyle:off magic.number
class ArrayReplaceSetTFSpec extends WordSpec with Matchers {

  val Path = List(1, 2)
  val Y = JString("Y")
  val X = JString("X")

  "A ArrayReplaceSetTF" when {
    "tranforming an array replace against an array set" must {

      /**
       * A-PS-1
       */
      "noOp the server's replace operation and not transform the client's set operation" in {
        val s = ArrayReplaceOperation(Path, false, 4, X)
        val c = ArraySetOperation(Path, false, JArray(List(Y)))

        val (s1, c1) = ArrayReplaceSetTF.transform(s, c)

        s1 shouldBe ArrayReplaceOperation(Path, true, 4, X)
        c1 shouldBe c
      }
    }
  }
}
