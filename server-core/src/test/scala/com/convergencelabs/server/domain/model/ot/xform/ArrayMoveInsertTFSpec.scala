package com.convergencelabs.server.domain.model.ot

import org.json4s.JsonAST.JString
import org.scalatest.Finders
import org.scalatest.Matchers
import org.scalatest.WordSpec

class ArrayMoveInsertTFSpec
    extends WordSpec
    with Matchers {

  val Path = List(1, 2)

  "A ArrayMoveInsertTF" when {
    "tranforming an forward move against an insert" must {

      /**
       * <pre>
       *
       * Indices         : [0, 1, 2, 3, 4, 5, 6, 7, 8, 9]
       * Original Array  : [A, B, C, D, E, F, G, H, I, J]
       *
       * Server Op       :           ^-->--^                     Move(3, 5)
       * Client Op       :        ^                              Insert(2, X)
       *
       * Server State    : [A, B, C, E, F, D, G, H, I, J]
       * Client Op'      :        ^                              Insert(2, X)
       *
       * Client State    : [A, B, X, C, D, E, F, G, H, I, J]
       * Server Op'      :              ^-->--^                  Move(4, 6)
       *
       * Converged State : [A, B, X, C, E, F, D, G, H, I, J]
       *
       * </pre>
       */
      "increment the from and to indices of the move and not transform the insert if the insert is before the move." in {
        val s = ArrayMoveOperation(Path, false, 3, 5)
        val c = ArrayInsertOperation(Path, false, 2, JString("X"))

        val (s1, c1) = ArrayMoveInsertTF.transform(s, c)

        s1 shouldBe ArrayMoveOperation(Path, false, 4, 6)
        c1 shouldBe c
      }

      /**
       * <pre>
       *
       * Indices         : [0, 1, 2, 3, 4, 5, 6, 7, 8, 9]
       * Original Array  : [A, B, C, D, E, F, G, H, I, J]
       *
       * Server Op       :           ^-->--^                     Move(3, 5)
       * Client Op       :           ^                           Insert(3, X)
       *
       * Server State    : [A, B, C, E, F, D, G, H, I, J]
       * Client Op'      :           ^                           Insert(3, X)
       *
       * Client State    : [A, B, C, X, D, E, F, G, H, I, J]
       * Server Op'      :              ^-->--^                  Move(4, 6)
       *
       * Converged State : [A, B, C, X, E, F, D, G, H, I, J]
       *
       * </pre>
       */
      "increment the from and to indices of the move and not transform the insert, if the insert is at the start of the move" in {
        val s = ArrayMoveOperation(Path, false, 3, 5)
        val c = ArrayInsertOperation(Path, false, 3, JString("X"))

        val (s1, c1) = ArrayMoveInsertTF.transform(s, c)

        s1 shouldBe ArrayMoveOperation(Path, false, 4, 6)
        c1 shouldBe c
      }

      /**
       * <pre>
       *
       * Indices         : [0, 1, 2, 3, 4, 5, 6, 7, 8, 9]
       * Original Array  : [A, B, C, D, E, F, G, H, I, J]
       *
       * Server Op       :           ^-->--^                     Move(3, 5)
       * Client Op       :              ^                        Insert(4, X)
       *
       * Server State    : [A, B, C, E, F, D, G, H, I, J]
       * Client Op'      :           ^                           Insert(3, X)
       *
       * Client State    : [A, B, C, D, X, E, F, G, H, I, J]
       * Server Op'      :           ^-->-----^                  Move(3, 6)
       *
       * Converged State : [A, B, C, X, E, F, D, G, H, I, J]
       *
       * </pre>
       */
      "increment the to index of the move and decrement the insert index, if the insert in the middle of the move" in {
        val s = ArrayMoveOperation(Path, false, 3, 5)
        val c = ArrayInsertOperation(Path, false, 4, JString("X"))

        val (s1, c1) = ArrayMoveInsertTF.transform(s, c)

        s1 shouldBe ArrayMoveOperation(Path, false, 3, 6)
        c1 shouldBe ArrayInsertOperation(Path, false, 3, JString("X"))
      }

      /**
       * <pre>
       *
       * Indices         : [0, 1, 2, 3, 4, 5, 6, 7, 8, 9]
       * Original Array  : [A, B, C, D, E, F, G, H, I, J]
       *
       * Server Op       :           ^-->--^                     Move(3, 5)
       * Client Op       :                 ^                     Insert(5, X)
       *
       * Server State    : [A, B, C, E, F, D, G, H, I, J]
       * Client Op'      :              ^                        Insert(4, X)
       *
       * Client State    : [A, B, C, D, E, X, F, G, H, I, J]
       * Server Op'      :           ^-->-----^                  Move(3, 6)
       *
       * Converged State : [A, B, C, E, X, F, D, G, H, I, J]
       *
       * </pre>
       */
      "increment the to index of the move and decrement the insert index, if the insert is at the end of the move" in {
        val s = ArrayMoveOperation(Path, false, 3, 5)
        val c = ArrayInsertOperation(Path, false, 5, JString("X"))

        val (s1, c1) = ArrayMoveInsertTF.transform(s, c)

        s1 shouldBe ArrayMoveOperation(Path, false, 3, 6)
        c1 shouldBe ArrayInsertOperation(Path, false, 4, JString("X"))
      }

      /**
       * <pre>
       *
       * Indices         : [0, 1, 2, 3, 4, 5, 6, 7, 8, 9]
       * Original Array  : [A, B, C, D, E, F, G, H, I, J]
       *
       * Server Op       :           ^-->--^                     Move(3, 5)
       * Client Op       :                    ^                  Insert(6, X)
       *
       * Server State    : [A, B, C, E, F, D, G, H, I, J]
       * Client Op'      :              ^                        Insert(6, X)
       *
       * Client State    : [A, B, C, D, E, F, X, G, H, I, J]
       * Server Op'      :           ^-->--^                     Move(3, 5)
       *
       * Converged State : [A, B, C, E, F, D, X, G, H, I, J]
       *
       * </pre>
       */
      "transform neither operation, if the insert is after the move" in {
        val s = ArrayMoveOperation(Path, false, 3, 5)
        val c = ArrayInsertOperation(Path, false, 6, JString("X"))

        val (s1, c1) = ArrayMoveInsertTF.transform(s, c)

        s1 shouldBe s
        c1 shouldBe c
      }
    }
    
    "tranforming a backward move against an insert" must {

      /**
       * <pre>
       *
       * Indices         : [0, 1, 2, 3, 4, 5, 6, 7, 8, 9]
       * Original Array  : [A, B, C, D, E, F, G, H, I, J]
       *
       * Server Op       :           ^--<--^                     Move(5, 3)
       * Client Op       :        ^                              Insert(2, X)
       *
       * Server State    : [A, B, C, F, D, E, G, H, I, J]
       * Client Op'      :        ^                              Insert(2, X)
       *
       * Client State    : [A, B, X, C, D, E, F, G, H, I, J]
       * Server Op'      :              ^--<--^                  Move(6, 4)
       *
       * Converged State : [A, B, X, C, F, D, E, G, H, I, J]
       *
       * </pre>
       */
      "increment the from and to indices of the move and not transform the insert if the insert is before the move." in {
        val s = ArrayMoveOperation(Path, false, 5, 3)
        val c = ArrayInsertOperation(Path, false, 2, JString("X"))

        val (s1, c1) = ArrayMoveInsertTF.transform(s, c)

        s1 shouldBe ArrayMoveOperation(Path, false, 6, 4)
        c1 shouldBe c
      }

      /**
       * <pre>
       *
       * Indices         : [0, 1, 2, 3, 4, 5, 6, 7, 8, 9]
       * Original Array  : [A, B, C, D, E, F, G, H, I, J]
       *
       * Server Op       :           ^--<--^                     Move(5, 3)
       * Client Op       :           ^                           Insert(3, X)
       *
       * Server State    : [A, B, C, F, D, E, G, H, I, J]
       * Client Op'      :              ^                        Insert(4, X)
       *
       * Client State    : [A, B, C, X, D, E, F, G, H, I, J]
       * Server Op'      :           ^---<----^                  Move(6, 3)
       *
       * Converged State : [A, B, C, F, X, D, E, G, H, I, J]
       *
       * </pre>
       */
      "increment the from index of the move and increment the insert, if the insert is at the start of the move" in {
        val s = ArrayMoveOperation(Path, false, 5, 3)
        val c = ArrayInsertOperation(Path, false, 3, JString("X"))

        val (s1, c1) = ArrayMoveInsertTF.transform(s, c)

        s1 shouldBe ArrayMoveOperation(Path, false, 6, 3)
        c1 shouldBe ArrayInsertOperation(Path, false, 4, JString("X"))
      }

      /**
       * <pre>
       *
       * Indices         : [0, 1, 2, 3, 4, 5, 6, 7, 8, 9]
       * Original Array  : [A, B, C, D, E, F, G, H, I, J]
       *
       * Server Op       :           ^--<--^                     Move(5, 3)
       * Client Op       :              ^                        Insert(4, X)
       *
       * Server State    : [A, B, C, F, D, E, G, H, I, J]
       * Client Op'      :                 ^                     Insert(5, X)
       *
       * Client State    : [A, B, C, D, X, E, F, G, H, I, J]
       * Server Op'      :           ^---<----^                  Move(6, 3)
       *
       * Converged State : [A, B, C, F, D, X, E, G, H, I, J]
       *
       * </pre>
       */
      "increment the from index of the move and increment the insert, if the insert in the middle of the move" in {
        val s = ArrayMoveOperation(Path, false, 5, 3)
        val c = ArrayInsertOperation(Path, false, 4, JString("X"))

        val (s1, c1) = ArrayMoveInsertTF.transform(s, c)

        s1 shouldBe ArrayMoveOperation(Path, false, 6, 3)
        c1 shouldBe ArrayInsertOperation(Path, false, 5, JString("X"))
      }

      /**
       * <pre>
       *
       * Indices         : [0, 1, 2, 3, 4, 5, 6, 7, 8, 9]
       * Original Array  : [A, B, C, D, E, F, G, H, I, J]
       *
       * Server Op       :           ^--<--^                     Move(5, 3)
       * Client Op       :                 ^                     Insert(5, X)
       *
       * Server State    : [A, B, C, F, D, E, G, H, I, J]
       * Client Op'      :                    ^                  Insert(6, X)
       *
       * Client State    : [A, B, C, D, E, X, F, G, H, I, J]
       * Server Op'      :           ^---<----^                  Move(6, 3)
       *
       * Converged State : [A, B, C, E, X, F, D, G, H, I, J]
       *
       * </pre>
       */
      "increment the to index of the move and increment the insert index, if the insert is at the end of the move" in {
        val s = ArrayMoveOperation(Path, false, 5, 3)
        val c = ArrayInsertOperation(Path, false, 5, JString("X"))

        val (s1, c1) = ArrayMoveInsertTF.transform(s, c)

        s1 shouldBe ArrayMoveOperation(Path, false, 6, 3)
        c1 shouldBe ArrayInsertOperation(Path, false, 6, JString("X"))
      }

      /**
       * <pre>
       *
       * Indices         : [0, 1, 2, 3, 4, 5, 6, 7, 8, 9]
       * Original Array  : [A, B, C, D, E, F, G, H, I, J]
       *
       * Server Op       :           ^--<--^                     Move(5, 3)
       * Client Op       :                    ^                  Insert(6, X)
       *
       * Server State    : [A, B, C, F, D, E, G, H, I, J]
       * Client Op'      :                    ^                  Insert(6, X)
       *
       * Client State    : [A, B, C, D, E, F, X, G, H, I, J]
       * Server Op'      :           ^--<--^                     Move(5, 3)
       *
       * Converged State : [A, B, C, F, D, E, X, G, H, I, J]
       *
       * </pre>
       */
      "transform neither operation if the insert is after the move" in {
        val s = ArrayMoveOperation(Path, false, 5, 3)
        val c = ArrayInsertOperation(Path, false, 6, JString("X"))

        val (s1, c1) = ArrayMoveInsertTF.transform(s, c)

        s1 shouldBe s
        c1 shouldBe c
      }
    }
    
    "tranforming a identity move against an insert" must {

      /**
       * <pre>
       *
       * Indices         : [0, 1, 2, 3, 4, 5, 6, 7, 8, 9]
       * Original Array  : [A, B, C, D, E, F, G, H, I, J]
       *
       * Server Op       :           ^                           Move(3, 3)
       * Client Op       :        ^                              Insert(2, X)
       *
       * Server State    : [A, B, C, D, E, F, G, H, I, J]
       * Client Op'      :        ^                              Insert(2, X)
       *
       * Client State    : [A, B, X, C, D, E, F, G, H, I, J]
       * Server Op'      :              ^                        Move(4, 4)
       *
       * Converged State : [A, B, X, C, D, E, F, G, H, I, J]
       *
       * </pre>
       */
      "increment the from and to indices of the move and not transform the insert if the insert is before the move." in {
        val s = ArrayMoveOperation(Path, false, 3, 3)
        val c = ArrayInsertOperation(Path, false, 2, JString("X"))

        val (s1, c1) = ArrayMoveInsertTF.transform(s, c)

        s1 shouldBe ArrayMoveOperation(Path, false, 4, 4)
        c1 shouldBe c
      }

      /**
       * <pre>
       *
       * Indices         : [0, 1, 2, 3, 4, 5, 6, 7, 8, 9]
       * Original Array  : [A, B, C, D, E, F, G, H, I, J]
       *
       * Server Op       :           ^                           Move(3, 3)
       * Client Op       :           ^                           Insert(3, X)
       *
       * Server State    : [A, B, C, D, E, F, G, H, I, J]
       * Client Op'      :           ^                           Insert(3, X)
       *
       * Client State    : [A, B, C, X, D, E, F, G, H, I, J]
       * Server Op'      :              ^                        Move(4, 4)
       *
       * Converged State : [A, B, C, X, D, E, F, G, H, I, J]
       *
       * </pre>
       */
      "increment the from and to indices of the move and not transform the insert, if the insert is at the start of the move" in {
        val s = ArrayMoveOperation(Path, false, 3, 3)
        val c = ArrayInsertOperation(Path, false, 3, JString("X"))

        val (s1, c1) = ArrayMoveInsertTF.transform(s, c)

        s1 shouldBe ArrayMoveOperation(Path, false, 4, 4)
        c1 shouldBe c
      }

      /**
       * <pre>
       *
       * Indices         : [0, 1, 2, 3, 4, 5, 6, 7, 8, 9]
       * Original Array  : [A, B, C, D, E, F, G, H, I, J]
       *
       * Server Op       :           ^                           Move(3, 3)
       * Client Op       :              ^                        Insert(4, X)
       *
       * Server State    : [A, B, C, D, E, F, G, H, I, J]
       * Client Op'      :              ^                        Insert(4, X)
       *
       * Client State    : [A, B, C, D, X, E, F, G, H, I, J]
       * Server Op'      :           ^                           Move(3, 3)
       *
       * Converged State : [A, B, C, D, X, E, F, G, H, I, J]
       *
       * </pre>
       */
      "transform neither operaiton, if the insert is after the move" in {
        val s = ArrayMoveOperation(Path, false, 3, 3)
        val c = ArrayInsertOperation(Path, false, 4, JString("X"))

        val (s1, c1) = ArrayMoveInsertTF.transform(s, c)

        s1 shouldBe s
        c1 shouldBe c
      }
    }
  }
}
