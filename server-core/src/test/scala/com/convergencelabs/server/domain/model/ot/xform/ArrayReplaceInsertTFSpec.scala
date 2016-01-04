package com.convergencelabs.server.domain.model.ot

import org.json4s.JsonAST.JString
import org.scalatest.Finders
import org.scalatest.Matchers
import org.scalatest.WordSpec

// scalastyle:off magic.number
class ArrayReplaceInsertTFSpec extends WordSpec with Matchers {

  val Path = List(1, 2)
  val Y = JString("Y")
  val X = JString("X")

  "A ArrayRemoveInsertTF" when {
    "tranforming an array replace against an array insert" must {

      /**
       * A-PI-1
       */
      "transform neither operation if the server's replace index is less than the client's insert index" in {
        val s = ArrayReplaceOperation(Path, false, 2, X)
        val c = ArrayInsertOperation(Path, false, 3, Y)

        val (s1, c1) = ArrayReplaceInsertTF.transform(s, c)

        s1 shouldBe s
        c1 shouldBe c
      }

      /**
       * A-PI-2
       */
      "decrement the client's index if the server's index is equal to the client's index" in {
        val s = ArrayReplaceOperation(Path, false, 2, X)
        val c = ArrayInsertOperation(Path, false, 2, Y)

        val (s1, c1) = ArrayReplaceInsertTF.transform(s, c)

        s1 shouldBe ArrayReplaceOperation(Path, false, 3, X)
        c1 shouldBe c
      }

      /**
       * A-PI-3
       */
      "increment the server's index if the server's index is greater than the client's index" in {
        val s = ArrayReplaceOperation(Path, false, 3, X)
        val c = ArrayInsertOperation(Path, false, 2, Y)

        val (s1, c1) = ArrayReplaceInsertTF.transform(s, c)

        s1 shouldBe ArrayReplaceOperation(Path, false, 4, X)
        c1 shouldBe c
      }
    }
  }
}
