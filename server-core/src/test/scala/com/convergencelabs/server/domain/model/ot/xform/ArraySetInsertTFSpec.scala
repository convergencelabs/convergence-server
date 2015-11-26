package com.convergencelabs.server.domain.model.ot

import org.json4s.JsonAST.JArray
import org.json4s.JsonAST.JString
import org.scalatest.Finders
import org.scalatest.Matchers
import org.scalatest.WordSpec

class ArraySetInsertTFSpec extends WordSpec with Matchers {

  val Path = List(1, 2)
  val ClientVal = JString("x")
  val ServerVal = JString("y")

  "A ArraySetInsertTF" when {
    "tranforming an array set against an array insert" must {

      "noOp the client's insert operation and not transform the server's set operation" in {
        val s = ArraySetOperation(Path, false, JArray(List()))
        val c = ArrayInsertOperation(Path, false, 2, ClientVal)

        val (s1, c1) = ArraySetInsertTF.transform(s, c)

        s1 shouldBe s
        c1 shouldBe ArrayInsertOperation(Path, true, 2, ClientVal)
      }
    }
  }
}