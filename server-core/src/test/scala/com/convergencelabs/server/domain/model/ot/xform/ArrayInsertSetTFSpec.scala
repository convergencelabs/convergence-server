package com.convergencelabs.server.domain.model.ot

import org.json4s.JsonAST.JArray
import org.json4s.JsonAST.JString
import org.scalatest.Finders
import org.scalatest.Matchers
import org.scalatest.WordSpec

// scalastyle:off magic.number
class ArrayInsertSetTFSpec
    extends WordSpec
    with Matchers {

  val Path = List(1, 2)

  "A ArrayInsertSetTF" when {

    "tranforming a server insert against a client set " must {

      /**
       * A-IS-1
       */
      "noOp the server's insert and not transform the client's set" in {
        val s = ArrayInsertOperation(Path, false, 4, JString("X"))
        val c = ArraySetOperation(Path, false, JArray(List(JString("Y"))))

        val (s1, c1) = ArrayInsertSetTF.transform(s, c)

        s1 shouldBe ArrayInsertOperation(Path, true, 4, JString("X"))
        c1 shouldBe c
      }
    }
  }
}
