package com.convergencelabs.server.domain.model.ot

import org.json4s.JsonAST.JString
import org.scalatest.Finders
import org.scalatest.Matchers
import org.scalatest.WordSpec

/**
 * This test test the ArrayInsertInsertTF Operation Transformation function.
 */
// scalastyle:off magic.number
class ArrayInsertInsertTFSpec extends WordSpec with Matchers {

  val Path = List(1, 2)
  val ClientVal = JString("x")
  val ServerVal = JString("y")

  "A ArrayInsertInsertTF" when {

    "tranforming two insert operations " must {

      /**
       * A-II-1
       */
      "increment the client's index, when the server's index is less than the client's index" in {
        val s = ArrayInsertOperation(Path, false, 4, ServerVal)
        val c = ArrayInsertOperation(Path, false, 5, ClientVal)

        val (s1, c1) = ArrayInsertInsertTF.transform(s, c)

        s1 shouldBe s
        c1 shouldBe ArrayInsertOperation(Path, false, 6, ClientVal)
      }

      /**
       * A-II-1
       */
      "increment the client's index, when the server's index is equal to the client's index" in {
        val s = ArrayInsertOperation(Path, false, 4, ServerVal)
        val c = ArrayInsertOperation(Path, false, 4, ClientVal)

        val (s1, c1) = ArrayInsertInsertTF.transform(s, c)

        s1 shouldBe s
        c1 shouldBe ArrayInsertOperation(Path, false, 5, ClientVal)
      }

      /**
       * A-II-3
       */
      "increment the server's index, when the server's index is greater than the client's index" in {
        val s = ArrayInsertOperation(Path, false, 3, ServerVal)
        val c = ArrayInsertOperation(Path, false, 2, ClientVal)

        val (s1, c1) = ArrayInsertInsertTF.transform(s, c)

        s1 shouldBe ArrayInsertOperation(Path, false, 4, ServerVal)
        c1 shouldBe c
      }
    }
  }
}
