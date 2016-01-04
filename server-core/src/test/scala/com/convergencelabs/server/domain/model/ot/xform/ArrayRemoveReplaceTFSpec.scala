package com.convergencelabs.server.domain.model.ot

import org.json4s.JsonAST.JString
import org.scalatest.Finders
import org.scalatest.WordSpec
import org.scalatest.Matchers

// scalastyle:off magic.number
class ArrayRemoveReplaceTFSpec extends WordSpec with Matchers {

  val Path = List(1, 2)
  val X = JString("X")

  "A ArrayRemoveReplaceTF" when {
    "tranforming a remove against a remove" must {

      /**
       * A-RP-1
       */
      "decrement the client's index if the server's index is less than the client's index" in {
        val s = ArrayRemoveOperation(Path, false, 4)
        val c = ArrayReplaceOperation(Path, false, 5, X)

        val (s1, c1) = ArrayRemoveReplaceTF.transform(s, c)

        s1 shouldBe s
        c1 shouldBe ArrayReplaceOperation(Path, false, 4, X)
      }

      /**
       * A-RP-2
       */
      "noOp ther server's remove and convert the client's replace to an insert if the server's index is equal to the client's index" in {
        val s = ArrayRemoveOperation(Path, false, 4)
        val c = ArrayReplaceOperation(Path, false, 4, X)

        val (s1, c1) = ArrayRemoveReplaceTF.transform(s, c)

        s1 shouldBe ArrayRemoveOperation(Path, true, 4)
        c1 shouldBe ArrayInsertOperation(Path, false, 4, X)
      }

      /**
       * A-RP-3
       */
      "transform neither operation if the server's remove index is greater than the client's replace index" in {
        val s = ArrayRemoveOperation(Path, false, 5)
        val c = ArrayReplaceOperation(Path, false, 4, X)

        val (s1, c1) = ArrayRemoveReplaceTF.transform(s, c)

        s1 shouldBe s
        c1 shouldBe c
      }
    }
  }
}
