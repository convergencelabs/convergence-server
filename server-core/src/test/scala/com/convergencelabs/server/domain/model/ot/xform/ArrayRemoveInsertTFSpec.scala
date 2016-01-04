package com.convergencelabs.server.domain.model.ot

import org.json4s.JsonAST.JString
import org.scalatest.Finders
import org.scalatest.Matchers
import org.scalatest.WordSpec

// scalastyle:off magic.number
class ArrayRemoveInsertTFSpec
    extends WordSpec
    with Matchers {

  val Path = List(1, 2)
  val X = JString("x")

  "A ArrayRemoveInsertTF" when {
    "tranforming a remove against an insert" must {

      /**
       * A-RI-1
       */
      "decrement the client's index if the server's index is less than the client's index" in {
        val s = ArrayRemoveOperation(Path, false, 4)
        val c = ArrayInsertOperation(Path, false, 5, X)

        val (s1, c1) = ArrayRemoveInsertTF.transform(s, c)

        s1 shouldBe s
        c1 shouldBe ArrayInsertOperation(Path, false, 4, X)
      }

      /**
       * A-RI-2
       */
      "increment the server's index if the server's index is equal to the client's index" in {
        val s = ArrayRemoveOperation(Path, false, 4)
        val c = ArrayInsertOperation(Path, false, 4, X)

        val (s1, c1) = ArrayRemoveInsertTF.transform(s, c)

        s1 shouldBe ArrayRemoveOperation(Path, false, 5)
        c1 shouldBe c
      }

      /**
       * A-RI-3
       */
      "increment the server's index if the server's index is greater than the client's index" in {
        val s = ArrayRemoveOperation(Path, false, 5)
        val c = ArrayInsertOperation(Path, false, 4, X)

        val (s1, c1) = ArrayRemoveInsertTF.transform(s, c)

        s1 shouldBe ArrayRemoveOperation(Path, false, 6)
        c1 shouldBe c
      }
    }
  }
}
