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
  val ClientVal = JArray(List(JString("x")))
  val ServerVal = JString("y")

  "A ArrayInsertSetTF" when {

    "tranforming a server insert against a client remove " must {

      "noOp the server's operation and not transform the client's" in {
        val s = ArrayInsertOperation(Path, false, 2, ServerVal)
        val c = ArraySetOperation(Path, false, ClientVal)

        val (s1, c1) = ArrayInsertSetTF.transform(s, c)

        s1 shouldBe ArrayInsertOperation(Path, true, 2, ServerVal)
        c1 shouldBe c
      }
    }
  }
}
