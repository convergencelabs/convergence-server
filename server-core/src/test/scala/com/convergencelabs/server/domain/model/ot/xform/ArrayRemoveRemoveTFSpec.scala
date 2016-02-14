package com.convergencelabs.server.domain.model.ot

import org.json4s.JsonAST.JString
import org.scalatest.Finders
import org.scalatest.WordSpec
import org.scalatest.Matchers

// scalastyle:off magic.number multiple.string.literals
class ArrayRemoveRemoveTFSpec extends WordSpec with Matchers {

  val Path = List(1, 2)

  "A ArrayRemoveRemoveTF" when {
    "tranforming a remove against a remove" must {

      /**
       * A-RR-1
       */
      "decrement the client's index if the server's index is less than the client's index" in {
        val s = ArrayRemoveOperation(Path, false, 4)
        val c = ArrayRemoveOperation(Path, false, 5)

        val (s1, c1) = ArrayRemoveRemoveTF.transform(s, c)

        s1 shouldBe s
        c1 shouldBe ArrayRemoveOperation(Path, false, 4)
      }

      /**
       * A-RR-2
       */
      "decrement the client's index if the server's index is equal to the client's index" in {
        val s = ArrayRemoveOperation(Path, false, 4)
        val c = ArrayRemoveOperation(Path, false, 4)

        val (s1, c1) = ArrayRemoveRemoveTF.transform(s, c)

        s1 shouldBe ArrayRemoveOperation(Path, true, 4)
        c1 shouldBe ArrayRemoveOperation(Path, true, 4)
      }

      /**
       * A-RR-3
       */
      "increment the server's index if the server's index is greater than the client's index" in {
        val s = ArrayRemoveOperation(Path, false, 5)
        val c = ArrayRemoveOperation(Path, false, 4)

        val (s1, c1) = ArrayRemoveRemoveTF.transform(s, c)

        s1 shouldBe ArrayRemoveOperation(Path, false, 4)
        c1 shouldBe c
      }
    }
  }
}
