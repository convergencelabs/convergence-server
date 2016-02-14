package com.convergencelabs.server.domain.model.ot

import org.json4s.JsonAST.JString
import org.scalatest.Finders
import org.scalatest.Matchers
import org.scalatest.WordSpec

// scalastyle:off magic.number multiple.string.literals
class ArrayReplaceReplaceTFSpec extends WordSpec with Matchers {

  val Path = List(1, 2)
  val X = JString("X")
  val Y = JString("Y")

  "A ArrayReplaceReplaceTF" when {
    "tranforming an array replace against an array insert" must {

      /**
       * A-PP-1
       */
      "transform neither operation if the server's replace index is less than the client's remove index" in {
        val s = ArrayReplaceOperation(Path, false, 4, X)
        val c = ArrayReplaceOperation(Path, false, 5, Y)

        val (s1, c1) = ArrayReplaceReplaceTF.transform(s, c)

        s1 shouldBe s
        c1 shouldBe c
      }

      /**
       * A-PP-2
       */
      "noOp the remove and convert the replace to an insert if the server's index is equal to the client's index" in {
        val s = ArrayReplaceOperation(Path, false, 4, X)
        val c = ArrayReplaceOperation(Path, false, 4, Y)

        val (s1, c1) = ArrayReplaceReplaceTF.transform(s, c)

        s1 shouldBe ArrayReplaceOperation(Path, false, 4, X)
        c1 shouldBe ArrayReplaceOperation(Path, true, 4, Y)
      }

      /**
       * A-PP-3
       */
      "noOp both operations if the index and values are identical" in {
        val s = ArrayReplaceOperation(Path, false, 4, X)
        val c = ArrayReplaceOperation(Path, false, 4, X)

        val (s1, c1) = ArrayReplaceReplaceTF.transform(s, c)

        s1 shouldBe ArrayReplaceOperation(Path, true, 4, X)
        c1 shouldBe ArrayReplaceOperation(Path, true, 4, X)
      }
    }
  }
}
