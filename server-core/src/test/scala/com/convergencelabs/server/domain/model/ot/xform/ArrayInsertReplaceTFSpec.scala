package com.convergencelabs.server.domain.model.ot

import org.json4s.JsonAST.JString
import org.scalatest.Finders
import org.scalatest.WordSpec
import org.scalatest.Matchers

// scalastyle:off magic.number
class ArrayInsertReplaceTFSpec
    extends WordSpec
    with Matchers {

  val Path = List(1, 2)
  val ClientVal = JString("x")
  val ServerVal = JString("y")

  "A ArrayInsertReplaceTF" when {

    "tranforming a server insert against a client remove " must {

      "increment the client's index if both operations target the same index" in {
        val s = ArrayInsertOperation(Path, false, 2, ServerVal)
        val c = ArrayReplaceOperation(Path, false, 2, ClientVal)

        val (s1, c1) = ArrayInsertReplaceTF.transform(s, c)

        s1 shouldBe s
        c1 shouldBe ArrayReplaceOperation(Path, false, 3, ClientVal)
      }

      "increment the client's index if the server's index is before the client's" in {
        val s = ArrayInsertOperation(Path, false, 2, ServerVal)
        val c = ArrayReplaceOperation(Path, false, 3, ClientVal)

        val (s1, c1) = ArrayInsertReplaceTF.transform(s, c)

        s1 shouldBe s
        c1 shouldBe ArrayReplaceOperation(Path, false, 4, ClientVal)
      }

      "leave both operations the same if the client's replace is before the server's insert" in {
        val s = ArrayInsertOperation(Path, false, 3, ServerVal)
        val c = ArrayReplaceOperation(Path, false, 2, ClientVal)

        val (s1, c1) = ArrayInsertReplaceTF.transform(s, c)

        s1 shouldBe s
        c1 shouldBe c
      }
    }
  }
}
