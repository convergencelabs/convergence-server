package com.convergencelabs.server.domain.model.ot

import org.json4s.JsonAST.JString
import org.scalatest.Finders
import org.scalatest.Matchers
import org.scalatest.WordSpec

// scalastyle:off magic.number
class ArrayRemoveMoveTFSpec
    extends WordSpec
    with Matchers {

  val Path = List(1, 2)

  "A ArrayRemoveMoveTF" when {
    "tranforming a remove against a forward move" must {

      /**
       * <pre>
       *
       * Indices         : [0, 1, 2, 3, 4, 5, 6, 7, 8, 9]
       * Original Array  : [A, B, C, D, E, F, G, H, I, J]
       *
       * Server Op       :        ^                            Remove(2, C)
       * Client Op       :           ^-->--^                   Move(3, 5)
       *
       * Server State    : [A, B, D, E, F, G, H, I, J]
       * Client Op'      :        ^-->--^                      Move(2, 4)
       *
       * Client State    : [A, B, C, E, F, D, G, H, I, J]
       * Server Op'      :        ^                            Remove(2, C)
       *
       * Converged State : [A, B, E, F, D, G, H, I, J]
       *
       * </pre>
       */
      "decrement the from and to indices of the move and not transform the remove if the remove is before the move." in {
        val s = ArrayRemoveOperation(Path, false, 2)
        val c = ArrayMoveOperation(Path, false, 3, 5)

        val (s1, c1) = ArrayRemoveMoveTF.transform(s, c)

        s1 shouldBe s
        c1 shouldBe ArrayMoveOperation(Path, false, 2, 4)
      }

      /**
       * <pre>
       *
       * Indices         : [0, 1, 2, 3, 4, 5, 6, 7, 8, 9]
       * Original Array  : [A, B, C, D, E, F, G, H, I, J]
       *
       * Server Op       :           ^                         Remove(3, D)
       * Client Op       :           ^-->--^                   Move(3, 5)
       *
       * Server State    : [A, B, C, E, F, G, H, I, J]
       * Client Op'      :           ^-->--^                   Move(3, 5) - NoOp
       *
       * Client State    : [A, B, C, E, F, D, G, H, I, J]
       * Server Op'      :                 ^                   Remove(3, D)
       *
       * Converged State : [A, B, C, E, F, G, H, I, J]
       *
       * </pre>
       */
      "noOp the server move and set the client remove index to the server move toIndex, if the remove is at the start of the move" in {
        val s = ArrayRemoveOperation(Path, false, 3)
        val c = ArrayMoveOperation(Path, false, 3, 5)

        val (s1, c1) = ArrayRemoveMoveTF.transform(s, c)

        s1 shouldBe ArrayRemoveOperation(Path, false, 5)
        c1 shouldBe ArrayMoveOperation(Path, true, 3, 5)
      }

      /**
       * <pre>
       *
       * Indices         : [0, 1, 2, 3, 4, 5, 6, 7, 8, 9]
       * Original Array  : [A, B, C, D, E, F, G, H, I, J]
       *
       * Client Op       :              ^                      Remove(4, E)
       * Server Op       :           ^-->--^                   Move(3, 5)
       *
       * Server State    : [A, B, C, D, F, G, H, I, J]
       * Client Op'      :           ^->^                      Move(3, 4)
       *
       * Client State    : [A, B, C, E, F, D, G, H, I, J]
       * Server Op'      :           ^                         Remove(3, E)
       *
       * Converged State : [A, B, C, F, D, G, H, I, J]
       *
       * </pre>
       */
      "decrement the server move toIndex and decrement the remove index, if the remove in the middle of the move" in {
        val s = ArrayRemoveOperation(Path, false, 4)
        val c = ArrayMoveOperation(Path, false, 3, 5)

        val (s1, c1) = ArrayRemoveMoveTF.transform(s, c)

        s1 shouldBe ArrayRemoveOperation(Path, false, 3)
        c1 shouldBe ArrayMoveOperation(Path, false, 3, 4)
      }

      /**
       * <pre>
       *
       * Indices         : [0, 1, 2, 3, 4, 5, 6, 7, 8, 9]
       * Original Array  : [A, B, C, D, E, F, G, H, I, J]
       *
       * Server Op       :                 ^                   Remove(5, F)
       * Client Op       :           ^-->--^                   Move(3, 5)
       *
       * Server State    : [A, B, C, D, E, G, H, I, J]
       * Client Op'      :           ^->^                      Move(3, 4)
       *
       * Client State    : [A, B, C, E, F, D, G, H, I, J]
       * Server Op'      :              ^                      Remove(4, F)
       *
       * Converged State : [A, B, C, E, D, G, H, I, J]
       *
       * </pre>
       */
      "decrement the server move toIndex and decrement the remove index, if the remove is at the end of the move" in {
        val s = ArrayRemoveOperation(Path, false, 5)
        val c = ArrayMoveOperation(Path, false, 3, 5)

        val (s1, c1) = ArrayRemoveMoveTF.transform(s, c)

        s1 shouldBe ArrayRemoveOperation(Path, false, 4)
        c1 shouldBe ArrayMoveOperation(Path, false, 3, 4)
      }

      /**
       * <pre>
       *
       * Indices         : [0, 1, 2, 3, 4, 5, 6, 7, 8, 9]
       * Original Array  : [A, B, C, D, E, F, G, H, I, J]
       *
       * Server Op       :                    ^                  Remove(6, G)
       * Client Op       :           ^-->--^                     Move(3, 5)
       *
       * Server State    : [A, B, C, E, F, D, H, I, J]
       * Client Op'      :           ^-->--^                     Move(3, 5)
       *
       * Client State    : [A, B, C, E, F, D, G, H, I, J]
       * Server Op'      :                    ^                  Remove(6, G)
       *
       * Converged State : [A, B, C, E, F, D, H, I, J]
       *
       * </pre>
       */
      "transform neither operation, if the remove is after the move" in {
        val s = ArrayRemoveOperation(Path, false, 6)
        val c = ArrayMoveOperation(Path, false, 3, 5)

        val (s1, c1) = ArrayRemoveMoveTF.transform(s, c)

        s1 shouldBe s
        c1 shouldBe c
      }
    }

    "tranforming a backward move against a remove" must {

      /**
       * <pre>
       *
       * Indices         : [0, 1, 2, 3, 4, 5, 6, 7, 8, 9]
       * Original Array  : [A, B, C, D, E, F, G, H, I, J]
       *
       * Server Op       :        ^                            Remove(1, C)
       * Client Op       :           ^--<--^                   Move(5, 3)
       *
       * Server State    : [A, B, D, E, F, G, H, I, J]
       * Client Op'      :        ^--<--^                      Move(4, 2)
       *
       * Client State    : [A, B, C, F, D, E, G, H, I, J]
       * Server Op'      :        ^                            Remove(1, C)
       *
       * Converged State : [A, B, F, D, E, G, H, I, J]
       *
       * </pre>
       */
      "decrement the from and to indices of the move and not transform the remove, if the remove is before the move." in {
        val s = ArrayRemoveOperation(Path, false, 1)
        val c = ArrayMoveOperation(Path, false, 5, 3)

        val (s1, c1) = ArrayRemoveMoveTF.transform(s, c)

        s1 shouldBe s
        c1 shouldBe ArrayMoveOperation(Path, false, 4, 2)
      }

      /**
       * <pre>
       *
       * Indices         : [0, 1, 2, 3, 4, 5, 6, 7, 8, 9]
       * Original Array  : [A, B, C, D, E, F, G, H, I, J]
       *
       * Server Op       :           ^                         Remove(3, D)
       * Client Op       :           ^--<--^                   Move(5, 3)
       *
       * Server State    : [A, B, C, E, F, G, H, I, J]
       * Client Op'      :           ^<-^                      Move(4, 3)
       *
       * Client State    : [A, B, C, F, D, E, G, H, I, J]
       * Server Op'      :              ^                      Remove(4, D)
       *
       * Converged State : [A, B, C, F, E, G, H, I, J]
       *
       * </pre>
       */
      "decrement the from index of the move and increment the remove, if the remove is at the start of the move" in {
        val s = ArrayRemoveOperation(Path, false, 3)
        val c = ArrayMoveOperation(Path, false, 5, 3)

        val (s1, c1) = ArrayRemoveMoveTF.transform(s, c)

        s1 shouldBe ArrayRemoveOperation(Path, false, 4)
        c1 shouldBe ArrayMoveOperation(Path, false, 4, 3)
      }

      /**
       * <pre>
       *
       * Indices         : [0, 1, 2, 3, 4, 5, 6, 7, 8, 9]
       * Original Array  : [A, B, C, D, E, F, G, H, I, J]
       *
       * Server Op       :              ^                      Remove(4, E)
       * Client Op       :           ^--<--^                   Move(5, 3)
       *
       * Server State    : [A, B, C, D, F, G, H, I, J]
       * Client Op'      :           ^<-^                      Move(4, 3)
       *
       * Client State    : [A, B, C, F, D, E, G, H, I, J]
       * Server Op'      :                 ^                   Remove(5, E)
       *
       * Converged State : [A, B, C, F, D, G, H, I, J]
       *
       * </pre>
       */
      "decrement the from index of the move and increment the remove, if the remove in the middle of the move" in {
        val s = ArrayRemoveOperation(Path, false, 4)
        val c = ArrayMoveOperation(Path, false, 5, 3)

        val (s1, c1) = ArrayRemoveMoveTF.transform(s, c)

        s1 shouldBe ArrayRemoveOperation(Path, false, 5)
        c1 shouldBe ArrayMoveOperation(Path, false, 4, 3)
      }

      /**
       * <pre>
       *
       * Indices         : [0, 1, 2, 3, 4, 5, 6, 7, 8, 9]
       * Original Array  : [A, B, C, D, E, F, G, H, I, J]
       *
       * Server Op       :                 ^                   Remove(5, F)
       * Client Op       :           ^-->--^                   Move(5, 3)
       *
       * Server State    : [A, B, C, D, E, G, H, I, J]
       * Client Op'      :           ^<-^                      Move(5, 3) - NoOp
       *
       * Client State    : [A, B, C, F, D, E, G, H, I, J]
       * Server Op'      :           ^                         Remove(3, F)
       *
       * Converged State : [A, B, C, D, E, G, H, I, J]
       *
       * </pre>
       */
      "noOp the move and set the remove index to the move's toIndex, if the remove is at the end of the move" in {
        val s = ArrayRemoveOperation(Path, false, 5)
        val c = ArrayMoveOperation(Path, false, 5, 3)

        val (s1, c1) = ArrayRemoveMoveTF.transform(s, c)

        s1 shouldBe ArrayRemoveOperation(Path, false, 3)
        c1 shouldBe ArrayMoveOperation(Path, true, 5, 3)
      }

      /**
       * <pre>
       *
       * Indices         : [0, 1, 2, 3, 4, 5, 6, 7, 8, 9]
       * Original Array  : [A, B, C, D, E, F, G, H, I, J]
       *
       * Server Op       :                    ^                Remove(6, G)
       * Client Op       :           ^--<--^                   Move(5, 3)
       *
       * Server State    : [A, B, C, D, E, F, H, I, J]
       * Client Op'      :           ^--<--^                   Move(5, 3)
       *
       * Client State    : [A, B, C, F, D, E, G, H, I, J]
       * Server Op'      :                    ^                Remove(6, G)
       *
       * Converged State : [A, B, C, F, D, E, H, I, J]
       *
       * </pre>
       */
      "transform neither operation if the remove is after the move" in {
        val s = ArrayRemoveOperation(Path, false, 6)
        val c = ArrayMoveOperation(Path, false, 5, 3)

        val (s1, c1) = ArrayRemoveMoveTF.transform(s, c)

        s1 shouldBe s
        c1 shouldBe c
      }
    }

    "tranforming a identity move against a remove" must {

      /**
       * <pre>
       *
       * Indices         : [0, 1, 2, 3, 4, 5, 6, 7, 8, 9]
       * Original Array  : [A, B, C, D, E, F, G, H, I, J]
       *
       * Server Op       :        ^                            Remove(2, C)
       * Client Op       :           ^                         Move(3, 3)
       *
       * Server State    : [A, C, D, E, F, G, H, I, J]
       * Client Op'      :        ^                            Move(2, 2)
       *
       * Client State    : [A, B, C, D, E, F, G, H, I, J]
       * Server Op'      :     ^                               Remove(2, C)
       *
       * Converged State : [A, B, D, E, F, G, H, I, J]
       *
       * </pre>
       */
      "decrement the from and to indices of the move and not transform the remove if the remove is before the move." in {
        val s = ArrayRemoveOperation(Path, false, 1)
        val c = ArrayMoveOperation(Path, false, 3, 3)

        val (s1, c1) = ArrayRemoveMoveTF.transform(s, c)

        s1 shouldBe s
        c1 shouldBe ArrayMoveOperation(Path, false, 2, 2)
      }

      /**
       * <pre>
       *
       * Indices         : [0, 1, 2, 3, 4, 5, 6, 7, 8, 9]
       * Original Array  : [A, B, C, D, E, F, G, H, I, J]
       *
       * Server Op       :           ^                         Remove(3, D)
       * Client Op       :           ^                         Move(3, 3)
       *
       * Server State    : [A, B, C, E, F, G, H, I, J]
       * Client Op'      :           ^                         Move(3, 3)
       *
       * Client State    : [A, B, C, D, E, F, G, H, I, J]
       * Server Op'      :           ^                         Remove(3, D)
       *
       * Converged State : [A, B, C, E, F, G, H, I, J]
       *
       * </pre>
       */
      "noOp the move, and do not transform the remove, if the remove is at the start of the move" in {
        val s = ArrayRemoveOperation(Path, false, 3)
        val c = ArrayMoveOperation(Path, false, 3, 3)

        val (s1, c1) = ArrayRemoveMoveTF.transform(s, c)

        s1 shouldBe s
        c1 shouldBe ArrayMoveOperation(Path, true, 3, 3)
      }

      /**
       * <pre>
       *
       * Indices         : [0, 1, 2, 3, 4, 5, 6, 7, 8, 9]
       * Original Array  : [A, B, C, D, E, F, G, H, I, J]
       *
       * Server Op       :              ^                        Remove(4, E)
       * Client Op       :           ^                           Move(3, 3)
       *
       * Server State    : [A, B, C, D, F, G, H, I, J]
       * Client Op'      :           ^                           Move(3, 3)
       *
       * Client State    : [A, B, C, D, E, F, G, H, I, J]
       * Server Op'      :              ^                     Remove(4, E)
       *
       * Converged State : [A, B, C, D, F, G, H, I, J]
       *
       * </pre>
       */
      "transform neither operaiton, if the remove is after the move" in {
        val s = ArrayRemoveOperation(Path, false, 4)
        val c = ArrayMoveOperation(Path, false, 3, 3)

        val (s1, c1) = ArrayRemoveMoveTF.transform(s, c)

        s1 shouldBe s
        c1 shouldBe c
      }
    }
  }
}
