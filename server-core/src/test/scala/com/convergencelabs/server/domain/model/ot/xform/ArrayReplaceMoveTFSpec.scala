package com.convergencelabs.server.domain.model.ot

import org.json4s.JsonAST.JString
import org.scalatest.Finders
import org.scalatest.Matchers
import org.scalatest.WordSpec

// scalastyle:off magic.number
class ArrayReplaceMoveTFSpec
    extends WordSpec
    with Matchers {

  val Path = List(1, 2)
  val X = JString("X")

  "A ArrayReplaceMoveTF" when {
    "tranforming an forward move against an replace" must {

      /**
       * <pre>
       *
       * Indices         : [0, 1, 2, 3, 4, 5, 6, 7, 8, 9]
       * Original Array  : [A, B, C, D, E, F, G, H, I, J]
       *
       * Server Op       :        ^                          Replace(2, X)
       * Client Op       :           ^-->--^                 Move(3, 5)
       *
       * Server State    : [A, B, C, D, E, F, G, H, I, J]
       * Client Op'      :           ^-->--^                 Move(3, 5)
       *
       * Client State    : [A, B, C, E, F, D, G, H, I, J]
       * Server Op'      :        ^                          Replace(2, X)
       *
       * Converged State : [A, B, X, E, F, D, G, H, I, J]
       *
       * </pre>
       */
      "transform neither operation if the replace is before the move." in {
        val s = ArrayReplaceOperation(Path, false, 2, X)
        val c = ArrayMoveOperation(Path, false, 3, 5)

        val (s1, c1) = ArrayReplaceMoveTF.transform(s, c)

        s1 shouldBe s
        c1 shouldBe c
      }

      /**
       * <pre>
       *
       * Indices         : [0, 1, 2, 3, 4, 5, 6, 7, 8, 9]
       * Original Array  : [A, B, C, D, E, F, G, H, I, J]
       *
       * Server Op       :           ^                         Replace(3, X)
       * Client Op       :           ^-->--^                   Move(3, 5)
       *
       * Server State    : [A, B, C, D, E, F, G, H, I, J]
       * Client Op'      :           ^-->--^                   Move(3, 5)
       *
       * Client State    : [A, B, C, E, F, D, G, H, I, J]
       * Server Op'      :                 ^                   Replace(5, X)
       *
       * Converged State : [A, B, C, E, F, X, G, H, I, J]
       *
       * </pre>
       */
      "no transform the move and set the replace index to the move's toIndex, if the replace is at the start of the move" in {
        val s = ArrayReplaceOperation(Path, false, 3, X)
        val c = ArrayMoveOperation(Path, false, 3, 5)

        val (s1, c1) = ArrayReplaceMoveTF.transform(s, c)

        s1 shouldBe ArrayReplaceOperation(Path, false, 5, X)
        c1 shouldBe c
      }

      /**
       * <pre>
       *
       * Indices         : [0, 1, 2, 3, 4, 5, 6, 7, 8, 9]
       * Original Array  : [A, B, C, D, E, F, G, H, I, J]
       *
       * Server Op       :              ^                      Replace(4, X)
       * Client Op       :           ^-->--^                   Move(3, 5)
       *
       * Server State    : [A, B, C, D, X, F, G, H, I, J]
       * Client Op'      :           ^-->--^                   Move(3, 5)
       *
       * Client State    : [A, B, C, E, F, D, G, H, I, J]
       * Server Op'      :           ^                         Replace(3, X)
       *
       * Converged State : [A, B, C, X, F, D, G, H, I, J]
       *
       * </pre>
       */
      "decrement the replace index and not transform the move, if the replace in the middle of the move" in {
        val s = ArrayReplaceOperation(Path, false, 4, X)
        val c = ArrayMoveOperation(Path, false, 3, 5)

        val (s1, c1) = ArrayReplaceMoveTF.transform(s, c)

        s1 shouldBe ArrayReplaceOperation(Path, false, 3, X)
        c1 shouldBe ArrayMoveOperation(Path, false, 3, 5)
      }

      /**
       * <pre>
       *
       * Indices         : [0, 1, 2, 3, 4, 5, 6, 7, 8, 9]
       * Original Array  : [A, B, C, D, E, F, G, H, I, J]
       *
       * Server Op       :                 ^                   Replace(5, X)
       * Client Op       :           ^-->--^                   Move(3, 5)
       *
       * Server State    : [A, B, C, D, E, X, G, H, I, J]
       * Client Op'      :           ^-->--^                   Move(3, 5)
       *
       * Client State    : [A, B, C, E, F, D, G, H, I, J]
       * Server Op'      :              ^                      Replace(4, X)
       *
       * Converged State : [A, B, C, E, X, D, G, H, I, J]
       *
       * </pre>
       */
      "not transform the move and decrement the replace index, if the replace is at the end of the move" in {
        val s = ArrayReplaceOperation(Path, false, 5, X)
        val c = ArrayMoveOperation(Path, false, 3, 5)

        val (s1, c1) = ArrayReplaceMoveTF.transform(s, c)

        s1 shouldBe ArrayReplaceOperation(Path, false, 4, X)
        c1 shouldBe c
      }

      /**
       * <pre>
       *
       * Indices         : [0, 1, 2, 3, 4, 5, 6, 7, 8, 9]
       * Original Array  : [A, B, C, D, E, F, G, H, I, J]
       *
       * Server Op       :                    ^                  Replace(6, X)
       * Client Op       :           ^-->--^                     Move(3, 5)
       *
       * Server State    : [A, B, C, D, E, F, X, H, I, J]
       * Client Op'      :           ^-->--^                     Move(3, 5)
       *
       * Client State    : [A, B, C, E, F, D, G, H, I, J]
       * Server Op'      :              ^                        Replace(6, X)
       *
       * Converged State : [A, B, C, E, F, D, X, H, I, J]
       *
       * </pre>
       */
      "transform neither operation, if the replace is after the move" in {
        val s = ArrayReplaceOperation(Path, false, 6, X)
        val c = ArrayMoveOperation(Path, false, 3, 5)

        val (s1, c1) = ArrayReplaceMoveTF.transform(s, c)

        s1 shouldBe s
        c1 shouldBe c
      }
    }

    "tranforming a backward move against an replace" must {

      /**
       * <pre>
       *
       * Indices         : [0, 1, 2, 3, 4, 5, 6, 7, 8, 9]
       * Original Array  : [A, B, C, D, E, F, G, H, I, J]
       *
       * Server Op       :        ^                            Replace(2, X)
       * Client Op       :           ^--<--^                   Move(5, 3)
       *
       * Server State    : [A, B, X, D, E, F, G, H, I, J]
       * Client Op'      :           ^--<--^                   Move(5, 3)
       *
       * Client State    : [A, B, C, F, D, E, G, H, I, J]
       * Server Op'      :        ^                            Replace(2, X)
       *
       * Converged State : [A, B, X, F, D, E, G, H, I, J]
       *
       * </pre>
       */
      "no transform either operation, if the replace is before the move." in {
        val s = ArrayReplaceOperation(Path, false, 2, X)
        val c = ArrayMoveOperation(Path, false, 5, 3)

        val (s1, c1) = ArrayReplaceMoveTF.transform(s, c)

        s1 shouldBe s
        c1 shouldBe c
      }

      /**
       * <pre>
       *
       * Indices         : [0, 1, 2, 3, 4, 5, 6, 7, 8, 9]
       * Original Array  : [A, B, C, D, E, F, G, H, I, J]
       *
       * Server Op       :           ^                         Replace(3, X)
       * Client Op       :           ^--<--^                   Move(5, 3)
       *
       * Server State    : [A, B, C, X, E, F, G, H, I, J]
       * Client Op'      :           ^--<--^                   Move(5, 3)
       *
       * Client State    : [A, B, C, F, D, E, G, H, I, J]
       * Server Op'      :              ^                      Replace(4, X)
       *
       * Converged State : [A, B, C, F, X, E, G, H, I, J]
       *
       * </pre>
       */
      "increment the replace and not transform the move, if the replace is at the start of the move" in {
        val s = ArrayReplaceOperation(Path, false, 3, X)
        val c = ArrayMoveOperation(Path, false, 5, 3)

        val (s1, c1) = ArrayReplaceMoveTF.transform(s, c)

        s1 shouldBe ArrayReplaceOperation(Path, false, 4, X)
        c1 shouldBe c
      }

      /**
       * <pre>
       *
       * Indices         : [0, 1, 2, 3, 4, 5, 6, 7, 8, 9]
       * Original Array  : [A, B, C, D, E, F, G, H, I, J]
       *
       * Server Op       :              ^                        Replace(4, X)
       * Client Op       :           ^--<--^                     Move(5, 3)
       *
       * Server State    : [A, B, C, D, X, F, G, H, I, J]
       * Client Op'      :           ^--<--^                     Move(5, 3)
       *
       * Client State    : [A, B, C, F, D, E, G, H, I, J]
       * Server Op'      :                 ^                     Replace(5, X)
       *
       * Converged State : [A, B, C, F, D, X, G, H, I, J]
       *
       * </pre>
       */
      "transform neither operation if the replace in the middle of the move" in {
        val s = ArrayReplaceOperation(Path, false, 4, X)
        val c = ArrayMoveOperation(Path, false, 5, 3)

        val (s1, c1) = ArrayReplaceMoveTF.transform(s, c)

        s1 shouldBe ArrayReplaceOperation(Path, false, 5, X)
        c1 shouldBe c
      }

      /**
       * <pre>
       *
       * Indices         : [0, 1, 2, 3, 4, 5, 6, 7, 8, 9]
       * Original Array  : [A, B, C, D, E, F, G, H, I, J]
       *
       * Server Op       :                 ^                   Replace(5, X)
       * Client Op       :           ^--<--^                   Move(5, 3)
       *
       * Server State    : [A, B, C, D, E, X, G, H, I, J]
       * Client Op'      :           ^--<--^                   Move(5, 3)
       *
       * Client State    : [A, B, C, F, D, E, G, H, I, J]
       * Server Op'      :           ^                         Replace(3, X)
       *
       * Converged State : [A, B, C, X, D, E, G, H, I, J]
       *
       * </pre>
       */
      "increment the to index of the move and increment the replace index, if the replace is at the end of the move" in {
        val s = ArrayReplaceOperation(Path, false, 5, X)
        val c = ArrayMoveOperation(Path, false, 5, 3)

        val (s1, c1) = ArrayReplaceMoveTF.transform(s, c)

        s1 shouldBe ArrayReplaceOperation(Path, false, 3, X)
        c1 shouldBe c
      }

      /**
       * <pre>
       *
       * Indices         : [0, 1, 2, 3, 4, 5, 6, 7, 8, 9]
       * Original Array  : [A, B, C, D, E, F, G, H, I, J]
       *
       * Server Op       :                    ^                  Replace(6, X)
       * Client Op       :           ^--<--^                     Move(5, 3)
       *
       * Server State    : [A, B, C, D, E, F, X, H, I, J]
       * Client Op'      :           ^--<--^                     Move(5, 3)
       *
       * Client State    : [A, B, C, F, D, E, G, H, I, J]
       * Server Op'      :                    ^                  Replace(6, X)
       *
       * Converged State : [A, B, C, F, D, E, X, H, I, J]
       *
       * </pre>
       */
      "transform neither operation if the replace is after the move" in {
        val s = ArrayReplaceOperation(Path, false, 6, X)
        val c = ArrayMoveOperation(Path, false, 5, 3)

        val (s1, c1) = ArrayReplaceMoveTF.transform(s, c)

        s1 shouldBe s
        c1 shouldBe c
      }
    }

    "tranforming a identity move against an replace" must {

      /**
       * <pre>
       *
       * Indices         : [0, 1, 2, 3, 4, 5, 6, 7, 8, 9]
       * Original Array  : [A, B, C, D, E, F, G, H, I, J]
       *
       * Server Op       :        ^                              Replace(2, X)
       * Client Op       :           ^                           Move(3, 3)
       *
       * Server State    : [A, B, C, D, E, F, G, H, I, J]
       * Client Op'      :           ^                           Move(3, 3)
       *
       * Client State    : [A, B, C, D, E, F, G, H, I, J]
       * Server Op'      :        ^                              Replace(2, X)
       *
       * Converged State : [A, B, X, D, E, F, G, H, I, J]
       *
       * </pre>
       */
      "transform neither operation if the replace is before the move." in {
        val s = ArrayReplaceOperation(Path, false, 1, X)
        val c = ArrayMoveOperation(Path, false, 3, 3)

        val (s1, c1) = ArrayReplaceMoveTF.transform(s, c)

        s1 shouldBe s
        c1 shouldBe c
      }

      /**
       * <pre>
       *
       * Indices         : [0, 1, 2, 3, 4, 5, 6, 7, 8, 9]
       * Original Array  : [A, B, C, D, E, F, G, H, I, J]
       *
       * Server Op       :           ^                         Replace(3, X)
       * Client Op       :           ^                         Move(3, 3)
       *
       * Server State    : [A, B, C, X, E, F, G, H, I, J]
       * Client Op'      :           ^                         Move(3, 3)
       *
       * Client State    : [A, B, C, D, E, F, G, H, I, J]
       * Server Op'      :           ^                         Replace(3, X)
       *
       * Converged State : [A, B, C, X, E, F, G, H, I, J]
       *
       * </pre>
       */
      "transform neither operation, if the replace is at the start of the move" in {
        val s = ArrayReplaceOperation(Path, false, 3, X)
        val c = ArrayMoveOperation(Path, false, 3, 3)

        val (s1, c1) = ArrayReplaceMoveTF.transform(s, c)

        s1 shouldBe s
        c1 shouldBe c
      }

      /**
       * <pre>
       *
       * Indices         : [0, 1, 2, 3, 4, 5, 6, 7, 8, 9]
       * Original Array  : [A, B, C, D, E, F, G, H, I, J]
       *
       * Server Op       :              ^                        Replace(4, X)
       * Client Op       :           ^                           Move(3, 3)
       *
       * Server State    : [A, B, C, D, X, F, G, H, I, J]
       * Client Op'      :           ^                           Move(3, 3)
       *
       * Client State    : [A, B, C, D, E, F, G, H, I, J]
       * Server Op'      :              ^                        Replace(4, X)
       *
       * Converged State : [A, B, C, D, X, F, G, H, I, J]
       *
       * </pre>
       */
      "transform neither operaiton, if the replace is after the move" in {
        val s = ArrayReplaceOperation(Path, false, 4, X)
        val c = ArrayMoveOperation(Path, false, 3, 3)

        val (s1, c1) = ArrayReplaceMoveTF.transform(s, c)

        s1 shouldBe s
        c1 shouldBe c
      }
    }
  }
}
