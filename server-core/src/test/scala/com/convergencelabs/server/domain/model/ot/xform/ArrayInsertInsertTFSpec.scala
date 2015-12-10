package com.convergencelabs.server.domain.model.ot

import org.json4s.JsonAST.JString
import org.scalatest.Finders
import org.scalatest.Matchers
import org.scalatest.WordSpec

// scalastyle:off magic.number
class ArrayInsertInsertTFSpec extends WordSpec with Matchers {

  val Path = List(1, 2)
  val ClientVal = JString("x")
  val ServerVal = JString("y")

  "A ArrayInsertInsertTF" when {

    "tranforming two insert operations " must {

      /**
       * <pre>
       *
       * Indices         : [0, 1, 2, 3, 4, 5, 6, 7, 8, 9]
       * Original Array  : [A, B, C, D, E, F, G, H, I, J]
       *
       * Server Op       :        ^                              Insert(2, X)
       * Client Op       :        ^                              Insert(2, Y)
       *
       * Server State    : [A, B, X, C, D, E, F, G, H, I, J]
       * Client Op'      :           ^                           Insert(3, Y)
       *
       * Client State    : [A, B, Y, C, D, E, F, G, H, I, J]
       * Server Op'      :        ^                              Insert(2, X)
       *
       * Converged State : [A, B, X, Y, C, D, E, F, G, H, I, J]
       *
       * </pre>
       */
      "increment the client's index if both operations target the same index" in {
        val s = ArrayInsertOperation(Path, false, 2, ServerVal)
        val c = ArrayInsertOperation(Path, false, 2, ClientVal)

        val (s1, c1) = ArrayInsertInsertTF.transform(s, c)

        s1 shouldBe s
        c1 shouldBe ArrayInsertOperation(Path, false, 3, ClientVal)
      }

      "increment the client's index if the server's index is before the client's" in {
        val s = ArrayInsertOperation(Path, false, 2, ServerVal)
        val c = ArrayInsertOperation(Path, false, 3, ClientVal)

        val (s1, c1) = ArrayInsertInsertTF.transform(s, c)

        s1 shouldBe s
        c1 shouldBe ArrayInsertOperation(Path, false, 4, ClientVal)
      }

      "increment the server's index if the server's index is after the client's" in {
        val s = ArrayInsertOperation(Path, false, 3, ServerVal)
        val c = ArrayInsertOperation(Path, false, 2, ClientVal)

        val (s1, c1) = ArrayInsertInsertTF.transform(s, c)

        s1 shouldBe ArrayInsertOperation(Path, false, 4, ServerVal)
        c1 shouldBe c
      }
    }
  }
}
