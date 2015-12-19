package com.convergencelabs.server.domain.model.ot

import org.json4s.JsonAST.JString
import org.scalatest.Finders
import org.scalatest.Matchers
import org.scalatest.WordSpec

class ArrayInsertMoveTFSpec
    extends WordSpec
    with Matchers {

  val Path = List(1, 2)

  "A ArrayInsertMoveTF" when {
    "tranforming an insert against a forward move" must {

      /**
       * <pre>
       *
       * Indices         : [0, 1, 2, 3, 4, 5, 6, 7, 8, 9]
       * Original Array  : [A, B, C, D, E, F, G, H, I, J]
       *
       * Server Op       :     ^                                 Insert(1, X)
       * Client Op       :           ^-->--^                     Move(3, 5)
       *
       * Server State    : [A, X, B, C, D, E, F, G, H, I, J]
       * Client Op'      :              ^-->--^                  Move(4, 6)
       * 
       * Client State    : [A, B, C, E, F, D, G, H, I, J]
       * Server Op'      :     ^                                 Insert(1, X)
       *
       * Converged State : [A, X, B, C, E, F, D, G, H, I, J]
       *
       * </pre>
       */
      "increment the from and to indices of the move and not transform the insert if the insert is before the move." in {
        val s = ArrayInsertOperation(Path, false, 1, JString("X"))
        val c = ArrayMoveOperation(Path, false, 3, 5)

        val (s1, c1) = ArrayInsertMoveTF.transform(s, c)

        s1 shouldBe s
        c1 shouldBe ArrayMoveOperation(Path, false, 4, 6)
      }

      /**
       * <pre>
       *
       * Indices         : [0, 1, 2, 3, 4, 5, 6, 7, 8, 9]
       * Original Array  : [A, B, C, D, E, F, G, H, I, J]
       *
       * Client Op       :           ^                           Insert(3, X)
       * Server Op       :           ^-->--^                     Move(3, 5)
       *
       * Server State    : [A, B, C, X, D, E, F, G, H, I, J]
       * Client Op'      :              ^-->--^                  Move(4, 6)
       *
       * Client State    : [A, B, C, E, F, D, G, H, I, J]
       * Server Op'      :           ^                           Insert(3, X)
       *
       * Converged State : [A, B, C, X, E, F, D, G, H, I, J]
       *
       * </pre>
       */
      "increment the from and to indices of the move and not transform the insert, if the insert is at the start of the move" in {
        val s = ArrayInsertOperation(Path, false, 3, JString("X"))
        val c = ArrayMoveOperation(Path, false, 3, 5)

        val (s1, c1) = ArrayInsertMoveTF.transform(s, c)

        s1 shouldBe s
        c1 shouldBe ArrayMoveOperation(Path, false, 4, 6)
      }

      /**
       * <pre>
       *
       * Indices         : [0, 1, 2, 3, 4, 5, 6, 7, 8, 9]
       * Original Array  : [A, B, C, D, E, F, G, H, I, J]
       *
       * Server Op       :              ^                        Insert(4, X)
       * Client Op       :           ^-->--^                     Move(3, 5)
       * 
       * Server State    : [A, B, C, D, X, E, F, G, H, I, J]
       * Client Op'      :           ^-->-----^                  Move(3, 6)
       * 
       * Client State    : [A, B, C, E, F, D, G, H, I, J]
       * Server Op'      :           ^                           Insert(3, X)
       *
       * Converged State : [A, B, C, X, E, F, D, G, H, I, J]
       *
       * </pre>
       */
      "increment the to index of the move and decrement the insert index, if the insert in the middle of the move" in {
        val s = ArrayInsertOperation(Path, false, 4, JString("X"))
        val c = ArrayMoveOperation(Path, false, 3, 5)

        val (s1, c1) = ArrayInsertMoveTF.transform(s, c)

        s1 shouldBe ArrayInsertOperation(Path, false, 3, JString("X"))
        c1 shouldBe ArrayMoveOperation(Path, false, 3, 6)
      }

      /**
       * The intention here seem to be tht
       * <pre>
       *
       * Indices         : [0, 1, 2, 3, 4, 5, 6, 7, 8, 9]
       * Original Array  : [A, B, C, D, E, F, G, H, I, J]
       *
       * Server Op       :                 ^                     Insert(5, X)
       * Client Op       :           ^-->--^                     Move(3, 5)
       * 
       * Server State    : [A, B, C, D, E, X, F, G, H, I, J]
       * Client Op'      :           ^-->-----^                  Move(3, 6)
       * 
       * Client State    : [A, B, C, E, F, D, G, H, I, J]
       * Server Op'      :              ^                        Insert(4, X)
       *
       * Converged State : [A, B, C, E, X, F, D, G, H, I, J]
       *
       * </pre>
       */
      "increment the to index of the move and decrement the insert index, if the insert is at the end of the move" in {
        val s = ArrayInsertOperation(Path, false, 5, JString("X"))
        val c = ArrayMoveOperation(Path, false, 3, 5)

        val (s1, c1) = ArrayInsertMoveTF.transform(s, c)

        s1 shouldBe ArrayInsertOperation(Path, false, 4, JString("X"))
        c1 shouldBe ArrayMoveOperation(Path, false, 3, 6)
      }

      /**
       * <pre>
       *
       * Indices         : [0, 1, 2, 3, 4, 5, 6, 7, 8, 9]
       * Original Array  : [A, B, C, D, E, F, G, H, I, J]
       *
       * Server Op       :                    ^                  Insert(6, X)
       * Client Op       :           ^-->--^                     Move(3, 5)
       *
       * Server State    : [A, B, C, D, E, F, X, G, H, I, J]
       * Client Op'      :           ^-->--^                     Move(3, 5)
       * 
       * Client State    : [A, B, C, E, F, D, G, H, I, J]
       * Server Op'      :              ^                        Insert(6, X)
       *
       * Converged State : [A, B, C, E, F, D, X, G, H, I, J]
       *
       * </pre>
       */
      "transform neither operation, if the insert is after the move" in {
        val s = ArrayInsertOperation(Path, false, 6, JString("X"))
        val c = ArrayMoveOperation(Path, false, 3, 5)

        val (s1, c1) = ArrayInsertMoveTF.transform(s, c)

        s1 shouldBe s
        c1 shouldBe c
      }
    }
    
    "tranforming a insert against a backward move" must {

      /**
       * <pre>
       *
       * Indices         : [0, 1, 2, 3, 4, 5, 6, 7, 8, 9]
       * Original Array  : [A, B, C, D, E, F, G, H, I, J]
       *
       * Client Op       :     ^                                 Insert(1, X)
       * Server Op       :           ^--<--^                     Move(5, 3)
       *
       * Server State    : [A, X, B, C, D, E, F, G, H, I, J]
       * Client Op'      :              ^--<--^                  Move(6, 4)
       * 
       * Client State    : [A, B, C, F, D, E, G, H, I, J]
       * Server Op'      :     ^                                 Insert(1, X)
       *
       * Converged State : [A, X, B, C, F, D, E, G, H, I, J]
       *
       * </pre>
       */
      "increment the from and to indices of the move and not transform the insert if the insert is before the move." in {
        val s = ArrayInsertOperation(Path, false, 1, JString("X"))
        val c = ArrayMoveOperation(Path, false, 5, 3)

        val (s1, c1) = ArrayInsertMoveTF.transform(s, c)

        s1 shouldBe s
        c1 shouldBe ArrayMoveOperation(Path, false, 6, 4)
      }

      /**
       * <pre>
       *
       * Indices         : [0, 1, 2, 3, 4, 5, 6, 7, 8, 9]
       * Original Array  : [A, B, C, D, E, F, G, H, I, J]
       *
       * Server Op       :           ^                           Insert(3, X)
       * Client Op       :           ^--<--^                     Move(5, 3)
       *
       * Server State    : [A, B, C, X, D, E, F, G, H, I, J]
       * Client Op'      :              ^--<--^                  Move(6, 4)
       * 
       * Client State    : [A, B, C, F, D, E, G, H, I, J]
       * Server Op'      :           ^                           Insert(3, X)
       *
       * Converged State : [A, B, C, X, F, D, E, G, H, I, J]
       *
       * </pre>
       */
      "increment the from index of the move and increment the insert, if the insert is at the start of the move" in {
        val s = ArrayInsertOperation(Path, false, 3, JString("X"))
        val c = ArrayMoveOperation(Path, false, 5, 3)

        val (s1, c1) = ArrayInsertMoveTF.transform(s, c)

        s1 shouldBe ArrayInsertOperation(Path, false, 3, JString("X"))
        c1 shouldBe ArrayMoveOperation(Path, false, 6, 4)
      }

      /**
       * <pre>
       *
       * Indices         : [0, 1, 2, 3, 4, 5, 6, 7, 8, 9]
       * Original Array  : [A, B, C, D, E, F, G, H, I, J]
       *
       * Server Op       :              ^                        Insert(4, X)
       * Client Op       :           ^--<--^                     Move(5, 3)
       *
       * Server State    : [A, B, C, D, X, E, F, G, H, I, J]
       * Client Op'      :           ^---<----^                  Move(6, 3)
       * 
       * Client State    : [A, B, C, F, D, E, G, H, I, J]
       * Server Op'      :                 ^                     Insert(5, X)
       *
       * Converged State : [A, B, C, X, E, F, D, G, H, I, J]
       *
       * </pre>
       */
      "increment the from index of the move and increment the insert, if the insert in the middle of the move" in {
        val s = ArrayInsertOperation(Path, false, 4, JString("X"))
        val c = ArrayMoveOperation(Path, false, 5, 3)

        val (s1, c1) = ArrayInsertMoveTF.transform(s, c)

        s1 shouldBe ArrayInsertOperation(Path, false, 5, JString("X"))
        c1 shouldBe ArrayMoveOperation(Path, false, 6, 3)
      }

      /**
       * Indices         : [0, 1, 2, 3, 4, 5, 6, 7, 8, 9]
       * Original Array  : [A, B, C, D, E, F, G, H, I, J]
       *
       * Server Op       :                 ^                     Insert(5, X)
       * Client Op       :           ^-->--^                     Move(5, 3)
       *
       * Server State    : [A, B, C, D, E, X, F, G, H, I, J]
       * Client Op'      :           ^---<----^                  Move(3, 6)
       * 
       * Client State    : [A, B, C, F, D, E, G, H, I, J]
       * Server Op'      :                    ^                  Insert(6, X)
       *
       * Converged State : [A, B, C, E, X, F, D, G, H, I, J]
       */
      "increment the to index of the move and increment the insert index, if the insert is at the end of the move" in {
        val s = ArrayInsertOperation(Path, false, 5, JString("X"))
        val c = ArrayMoveOperation(Path, false, 5, 3)

        val (s1, c1) = ArrayInsertMoveTF.transform(s, c)

        s1 shouldBe ArrayInsertOperation(Path, false, 6, JString("X"))
        c1 shouldBe ArrayMoveOperation(Path, false, 6, 3)
      }

      /**
       * <pre>
       *
       * Indices         : [0, 1, 2, 3, 4, 5, 6, 7, 8, 9]
       * Original Array  : [A, B, C, D, E, F, G, H, I, J]
       *
       * Server Op       :                    ^                  Insert(6, X)
       * Client Op       :           ^--<--^                     Move(5, 3)
       *
       * Server State    : [A, B, C, D, E, F, X, G, H, I, J]
       * Client Op'      :           ^--<--^                     Move(5, 3)
       * 
       * Client State    : [A, B, C, F, D, E, G, H, I, J]
       * Server Op'      :                    ^                  Insert(6, X)
       *
       * Converged State : [A, B, C, F, D, E, X, G, H, I, J]
       *
       * </pre>
       */
      "transform neither operation if the insert is after the move" in {
        val s = ArrayInsertOperation(Path, false, 6, JString("X"))
        val c = ArrayMoveOperation(Path, false, 5, 3)

        val (s1, c1) = ArrayInsertMoveTF.transform(s, c)

        s1 shouldBe s
        c1 shouldBe c
      }
    }
    
    "tranforming an insert against an identity move" must {

      /**
       * <pre>
       *
       * Indices         : [0, 1, 2, 3, 4, 5, 6, 7, 8, 9]
       * Original Array  : [A, B, C, D, E, F, G, H, I, J]
       *
       * Server Op       :     ^                                 Insert(1, X)
       * Client Op       :           ^                           Move(3, 3)
       *
       * Server State    : [A, X, B, C, D, E, F, G, H, I, J]
       * Client Op'      :              ^                        Move(4, 4)
       * 
       * Client State    : [A, B, C, D, E, F, G, H, I, J]
       * Server Op'      :     ^                                 Insert(1, X)
       *
       * Converged State : [A, X, B, C, D, E, F, G, H, I, J]
       *
       * </pre>
       */
      "increment the from and to indices of the move and not transform the insert if the insert is before the move." in {
        val s = ArrayInsertOperation(Path, false, 1, JString("X"))
        val c = ArrayMoveOperation(Path, false, 3, 3)

        val (s1, c1) = ArrayInsertMoveTF.transform(s, c)

        s1 shouldBe s
        c1 shouldBe ArrayMoveOperation(Path, false, 4, 4)
      }

      /**
       * <pre>
       *
       * Indices         : [0, 1, 2, 3, 4, 5, 6, 7, 8, 9]
       * Original Array  : [A, B, C, D, E, F, G, H, I, J]
       *
       * Server Op       :           ^                           Insert(3, X)
       * Client Op       :           ^                           Move(3, 3)
       *
       * Server State    : [A, B, C, X, D, E, F, G, H, I, J]
       * Client Op'      :              ^                        Move(4, 4)
       * 
       * Client State    : [A, B, C, D, E, F, G, H, I, J]
       * Server Op'      :           ^                           Insert(3, X)
       *
       * Converged State : [A, B, C, X, D, E, F, G, H, I, J]
       *
       * </pre>
       */
      "increment the from and to indices of the move and not transform the insert, if the insert is at the start of the move" in {
        val s = ArrayInsertOperation(Path, false, 3, JString("X"))
        val c = ArrayMoveOperation(Path, false, 3, 3)

        val (s1, c1) = ArrayInsertMoveTF.transform(s, c)

        s1 shouldBe s
        c1 shouldBe ArrayMoveOperation(Path, false, 4, 4)
      }

      /**
       * <pre>
       *
       * Indices         : [0, 1, 2, 3, 4, 5, 6, 7, 8, 9]
       * Original Array  : [A, B, C, D, E, F, G, H, I, J]
       *
       * Server Op       :              ^                        Insert(4, X)
       * Client Op       :           ^                           Move(3, 3)
       *
       * Server State    : [A, B, C, D, X, E, F, G, H, I, J]
       * Client Op'      :           ^                           Move(3, 3)
       * 
       * Client State    : [A, B, C, D, E, F, G, H, I, J]
       * Server Op'      :              ^                        Insert(4, X)
       *
       * Converged State : [A, B, C, X, D, E, F, G, H, I, J]
       *
       * </pre>
       */
      "transform neither operaiton, if the insert is after the move" in {
        val s = ArrayInsertOperation(Path, false, 4, JString("X"))
        val c = ArrayMoveOperation(Path, false, 3, 3)

        val (s1, c1) = ArrayInsertMoveTF.transform(s, c)

        s1 shouldBe s
        c1 shouldBe c
      }
    }
  }
}
