package com.convergencelabs.server.domain.model.ot

import org.json4s.JsonAST.JString
import org.scalatest.Finders
import org.scalatest.Matchers
import org.scalatest.WordSpec

class ArrayReplaceReplaceTFSpec extends WordSpec with Matchers {

  val Path = List(1, 2)
  val X = JString("X")
  val Y = JString("Y")

  "A ArrayReplaceReplaceTF" when {
    "tranforming an array replace against an array insert" must {

      /**
       * <pre>
       *
       * Indices         : [0, 1, 2, 3, 4, 5, 6, 7, 8, 9]
       * Original Array  : [A, B, C, D, E, F, G, H, I, J]
       *
       * Server Op       :        ^                              Replace(2, C, X)
       * Client Op       :           ^                           Replace(3, D, Y)
       *
       * Server State    : [A, B, X, D, E, F, G, H, I, J]
       * Client Op'      :           ^                           Replace(3, D, Y)
       *
       * Client State    : [A, B, C, Y, E, F, G, H, I, J]
       * Server Op'      :        ^                              Replace(2, C, X)
       *
       * Converged State : [A, B, X, Y, E, F, G, H, I, J]
       *
       * </pre>
       */
      "transform neither operation if the server's replace index is less than the client's remove index" in {
        val s = ArrayReplaceOperation(Path, false, 2, X)
        val c = ArrayReplaceOperation(Path, false, 3, Y)

        val (s1, c1) = ArrayReplaceReplaceTF.transform(s, c)

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
       * Client Op       :        ^                              Replace(2, C, Y)
       *
       * Server State    : [A, B, X, D, E, F, G, H, I, J]
       * Client Op'      :        ^                              Replace(2, C, Y) - NoOp
       *
       * Client State    : [A, B, Y, D, E, F, G, H, I, J]
       * Server Op'      :        ^                              Replace(2, Y, X)
       *
       * Converged State : [A, B, X, D, E, F, G, H, I, J]
       *
       * </pre>
       */
      "noOp the remove and convert the replace to an insert if the server's index is equal to the client's index" in {
        val s = ArrayReplaceOperation(Path, false, 2, X)
        val c = ArrayReplaceOperation(Path, false, 2, Y)

        val (s1, c1) = ArrayReplaceReplaceTF.transform(s, c)

        s1 shouldBe ArrayReplaceOperation(Path, false, 2, X)
        c1 shouldBe ArrayReplaceOperation(Path, true, 2, Y)
      }

      /**
       * <pre>
       *
       * Indices         : [0, 1, 2, 3, 4, 5, 6, 7, 8, 9]
       * Original Array  : [A, B, C, D, E, F, G, H, I, J]
       *
       * Server Op       :        ^                              Replace(2, C, X)
       * Client Op       :        ^                              Replace(2, C, X)
       *
       * Server State    : [A, B, X, D, E, F, G, H, I, J]
       * Client Op'      :        ^                              Replace(2, C, X) - NoOp
       *
       * Client State    : [A, B, X, D, E, F, G, H, I, J]
       * Server Op'      :        ^                              Replace(2, C, X) - NoOp
       *
       * Converged State : [A, B, X, D, E, F, G, H, I, J]
       *
       * </pre>
       */
      "noOp both operations if the index and values are identical" in {
        val s = ArrayReplaceOperation(Path, false, 2, X)
        val c = ArrayReplaceOperation(Path, false, 2, X)

        val (s1, c1) = ArrayReplaceReplaceTF.transform(s, c)

        s1 shouldBe ArrayReplaceOperation(Path, true, 2, X)
        c1 shouldBe ArrayReplaceOperation(Path, true, 2, X)
      }
    }
  }
}
