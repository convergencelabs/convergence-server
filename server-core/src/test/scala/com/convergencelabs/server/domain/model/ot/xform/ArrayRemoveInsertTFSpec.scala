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
  val ClientVal = JString("x")
  val ServerVal = JString("y")

  "A ArrayRemoveInsertTF" when {
    "tranforming a remove against an insert" must {

      /**
       * <pre>
       *
       * Indices         : [0, 1, 2, 3, 4, 5, 6, 7, 8, 9]
       * Original Array  : [A, B, C, D, E, F, G, H, I, J]
       *
       * Server Op       :        ^                              Remove(2, C)
       * Client Op       :           ^                           Insert(3, X)
       *
       * Server State    : [A, B, D, E, F, G, H, I, J]
       * Client Op'      :        ^                              Insert(2, X)
       *
       * Client State    : [A, B, C, D, X, E, F, G, H, I, J]
       * Server Op'      :        ^                              Remove(2, C)
       *
       * Converged State : [A, B, X, D, E, F, G, H, I, J]
       *
       * </pre>
       */
      "decrement the client's index if the server's index is less than the client's index" in {
        val s = ArrayRemoveOperation(Path, false, 2)
        val c = ArrayInsertOperation(Path, false, 3, ClientVal)

        val (s1, c1) = ArrayRemoveInsertTF.transform(s, c)

        s1 shouldBe s
        c1 shouldBe ArrayInsertOperation(Path, false, 2, ClientVal)
      }

      /**
       * <pre>
       *
       * Indices         : [0, 1, 2, 3, 4, 5, 6, 7, 8, 9]
       * Original Array  : [A, B, C, D, E, F, G, H, I, J]
       *
       * Server Op       :              ^                        Remove(4, E)
       * Client Op       :              ^                        Insert(4, X)
       *
       * Server State    : [A, B, C, D, F, G, H, I, J]
       * Client Op'      :              ^                        Insert(4, X)
       *
       * Client State    : [A, B, C, D, X, E, F, G, H, I, J]
       * Server Op'      :                 ^                     Remove(5, E)
       *
       * Converged State : [A, B, X, D, E, F, G, H, I, J]
       *
       * </pre>
       */
      "increment the server's index if the server's index is equal to the client's index" in {
        val s = ArrayRemoveOperation(Path, false, 4)
        val c = ArrayInsertOperation(Path, false, 4, ClientVal)

        val (s1, c1) = ArrayRemoveInsertTF.transform(s, c)

        s1 shouldBe ArrayRemoveOperation(Path, false, 5)
        c1 shouldBe c
      }

      /**
       * <pre>
       *
       * Indices         : [0, 1, 2, 3, 4, 5, 6, 7, 8, 9]
       * Original Array  : [A, B, C, D, E, F, G, H, I, J]
       *
       * Server Op       :              ^                        Remove(4, E)
       * Client Op       :        ^                              Insert(2, X)
       *
       * Server State    : [A, B, C, D, F, G, H, I, J]
       * Client Op'      :        ^                              Insert(2, X)
       *
       * Client State    : [A, B, X, C, D, E, F, G, H, I, J]
       * Server Op'      :                 ^                     Remove(5, E)
       *
       * Converged State : [A, B, X, C, D, F, G, H, I, J]
       *
       * </pre>
       */
      "increment the server's index if the server's index is greater than the client's index" in {
        val s = ArrayRemoveOperation(Path, false, 4)
        val c = ArrayInsertOperation(Path, false, 2, ClientVal)

        val (s1, c1) = ArrayRemoveInsertTF.transform(s, c)

        s1 shouldBe ArrayRemoveOperation(Path, false, 5)
        c1 shouldBe c
      }
    }
  }
}
