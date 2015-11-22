package com.convergencelabs.server.domain.model.ot

import org.json4s.JsonAST.JString
import org.scalatest.Finders
import org.scalatest.Matchers
import org.scalatest.WordSpec

class ArrayReplaceRemoveTFSpec extends WordSpec with Matchers {

  val Path = List(1, 2)
  val ClientVal = JString("x")
  val ServerVal = JString("y")

  "A ArrayReplaceRemoveTF" when {
    "tranforming an array replace against an array insert" must {

      /**
       * <pre>
       *
       * Indices         : [0, 1, 2, 3, 4, 5, 6, 7, 8, 9]
       * Original Array  : [A, B, C, D, E, F, G, H, I, J]
       *
       * Server Op       :        ^                              Replace(2, C, X)
       * Client Op       :           ^                           Remove(3, D)
       *
       * Server State    : [A, B, X, D, E, F, G, H, I, J]
       * Client Op'      :           ^                           Remove(3, D)
       *
       * Client State    : [A, B, C, E, F, G, H, I, J]
       * Server Op'      :        ^                              Replace(2, C, X)
       *
       * Converged State : [A, B, X, E, F, G, H, I, J]
       *
       * </pre>
       */
      "transform neither operation if the server's replace index is less than the client's remove index" in {
        val s = ArrayReplaceOperation(Path, false, 2, ServerVal)
        val c = ArrayRemoveOperation(Path, false, 3)

        val (s1, c1) = ArrayReplaceRemoveTF.transform(s, c)

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
       * Client Op       :        ^                              Remove(2, C)
       *
       * Server State    : [A, B, X, D, E, F, G, H, I, J]
       * Client Op'      :        ^                              Remove(2, C) - NoOp
       *
       * Client State    : [A, B, D, E, F, G, H, I, J]
       * Server Op'      :        ^                              Insert(2, X)
       *
       * Converged State : [A, B, Y, X, D, E, F, G, H, I, J]
       *
       * </pre>
       */
      "noOp the remove and convert the replace to an insert if the server's index is equal to the client's index" in {
        val s = ArrayReplaceOperation(Path, false, 2, ServerVal)
        val c = ArrayRemoveOperation(Path, false, 2)

        val (s1, c1) = ArrayReplaceRemoveTF.transform(s, c)

        s1 shouldBe ArrayInsertOperation(Path, false, 2, ServerVal)
        c1 shouldBe ArrayRemoveOperation(Path, true, 2)
      }

      /**
       * <pre>
       *
       * Indices         : [0, 1, 2, 3, 4, 5, 6, 7, 8, 9]
       * Original Array  : [A, B, C, D, E, F, G, H, I, J]
       *
       * Server Op       :           ^                           Replace(3, D, X)
       * Client Op       :        ^                              Remove(2, C)
       *
       * Server State    : [A, B, C, X, E, F, G, H, I, J]
       * Client Op'      :        ^                              Remove(2, C)
       *
       * Client State    : [A, B, D, E, F, G, H, I, J]
       * Server Op'      :        ^                              Replace(2, D, X)
       *
       * Converged State : [A, B, X, E, F, G, H, I, J]
       *
       * </pre>
       */
      "decrement the server's index if the server's index is greater than the client's index" in {
        val s = ArrayReplaceOperation(Path, false, 3, ServerVal)
        val c = ArrayRemoveOperation(Path, false, 2)

        val (s1, c1) = ArrayReplaceRemoveTF.transform(s, c)

        s1 shouldBe ArrayReplaceOperation(Path, false, 2, ServerVal)
        c1 shouldBe c
      }
    }
  }
}