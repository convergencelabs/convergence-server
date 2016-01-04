package com.convergencelabs.server.domain.model.ot

import org.json4s.JsonAST.JString
import org.scalatest.Finders
import org.scalatest.Matchers
import org.scalatest.WordSpec

// scalastyle:off magic.number
class ArrayReplaceRemoveTFSpec extends WordSpec with Matchers {

  val Path = List(1, 2)
  val X = JString("X")

  "A ArrayReplaceRemoveTF" when {
    "tranforming an array replace against an array insert" must {

      /**
       * A-PR-1
       */
      "transform neither operation if the server's replace index is less than the client's remove index" in {
        val s = ArrayReplaceOperation(Path, false, 4, X)
        val c = ArrayRemoveOperation(Path, false, 5)

        val (s1, c1) = ArrayReplaceRemoveTF.transform(s, c)

        s1 shouldBe s
        c1 shouldBe c
      }

      /**
       * A-PR-2
       */
      "noOp the remove and convert the replace to an insert if the server's index is equal to the client's index" in {
        val s = ArrayReplaceOperation(Path, false, 4, X)
        val c = ArrayRemoveOperation(Path, false, 4)

        val (s1, c1) = ArrayReplaceRemoveTF.transform(s, c)

        s1 shouldBe ArrayInsertOperation(Path, false, 4, X)
        c1 shouldBe ArrayRemoveOperation(Path, true, 4)
      }

      /**
       * A-PR-3
       */
      "decrement the server's index if the server's index is greater than the client's index" in {
        val s = ArrayReplaceOperation(Path, false, 5, X)
        val c = ArrayRemoveOperation(Path, false, 4)

        val (s1, c1) = ArrayReplaceRemoveTF.transform(s, c)

        s1 shouldBe ArrayReplaceOperation(Path, false, 4, X)
        c1 shouldBe c
      }
    }
  }
}
