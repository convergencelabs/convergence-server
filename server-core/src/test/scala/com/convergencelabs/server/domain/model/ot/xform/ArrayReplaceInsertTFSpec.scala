package com.convergencelabs.server.domain.model.ot

import org.json4s.JsonAST.JString
import org.scalatest.Finders
import org.scalatest.Matchers
import org.scalatest.WordSpec

class ArrayReplaceInsertTFSpec extends WordSpec with Matchers {

  val Path = List(1, 2)
  val ClientVal = JString("x")
  val ServerVal = JString("y")

  "A ArrayRemoveInsertTF" when {
    "tranforming an array replace against an array insert" must {

      /**
       * <pre>
       *
       * Indices         : [0, 1, 2, 3, 4, 5, 6, 7, 8, 9]
       * Original Array  : [A, B, C, D, E, F, G, H, I, J]
       *
       * Server Op       :        ^                              Replace(2, C, X)
       * Client Op       :           ^                           Insert(3, Y)
       *
       * Server State    : [A, B, X, D, E, F, G, H, I, J]
       * Client Op'      :           ^                           Insert(3, Y)
       *
       * Client State    : [A, B, C, Y, D, E, F, G, H, I, J]
       * Server Op'      :        ^                              Replace(2, C, X)
       *
       * Converged State : [A, B, X, Y, D, E, F, G, H, I, J]
       *
       * </pre>
       */
      "transform neither operation if the server's replace index is less than the client's insert index" in {
        val s = ArrayReplaceOperation(Path, false, 2, ServerVal)
        val c = ArrayInsertOperation(Path, false, 3, ClientVal)

        val (s1, c1) = ArrayReplaceInsertTF.transform(s, c)

        s1 shouldBe s
        c1 shouldBe c
      }

      /**
       * <pre>
       *
       * Indices         : [0, 1, 2, 3, 4, 5, 6, 7, 8, 9]
       * Original Array  : [A, B, C, D, E, F, G, H, I, J]
       *
       * Server Op       :        ^                              Replace(2, C, X)
       * Client Op       :        ^                              Insert(2, Y)
       *
       * Server State    : [A, B, X, D, E, F, G, H, I, J]
       * Client Op'      :        ^                              Insert(2, Y)
       *
       * Client State    : [A, B, Y, C, D, E, F, G, H, I, J]
       * Server Op'      :           ^                           Replace(3, C, X)
       *
       * Converged State : [A, B, Y, X, D, E, F, G, H, I, J]
       *
       * </pre>
       */
      "decrement the client's index if the server's index is equal to the client's index" in {
        val s = ArrayReplaceOperation(Path, false, 2, ServerVal)
        val c = ArrayInsertOperation(Path, false, 2, ClientVal)

        val (s1, c1) = ArrayReplaceInsertTF.transform(s, c)

        s1 shouldBe ArrayReplaceOperation(Path, false, 3, ServerVal)
        c1 shouldBe c
      }

      /**
       * <pre>
       *
       * Indices         : [0, 1, 2, 3, 4, 5, 6, 7, 8, 9]
       * Original Array  : [A, B, C, D, E, F, G, H, I, J]
       *
       * Server Op       :           ^                           Replace(3, D, X)
       * Client Op       :        ^                              Insert(2, Y)
       *
       * Server State    : [A, B, C, X, E, F, G, H, I, J]
       * Client Op'      :        ^                              Insert(2, Y)
       *
       * Client State    : [A, B, Y, C, D, E, F, G, H, I, J]
       * Server Op'      :              ^                        Replace(4, D, X)
       *
       * Converged State : [A, B, Y, C, X, D, F, G, H, I, J]
       *
       * </pre>
       */
      "increment the server's index if the server's index is greater than the client's index" in {
        val s = ArrayReplaceOperation(Path, false, 3, ServerVal)
        val c = ArrayInsertOperation(Path, false, 2, ClientVal)

        val (s1, c1) = ArrayReplaceInsertTF.transform(s, c)

        s1 shouldBe ArrayReplaceOperation(Path, false, 4, ServerVal)
        c1 shouldBe c
      }
    }
  }
}
