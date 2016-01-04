package com.convergencelabs.server.domain.model.ot

import org.json4s.JsonAST.JString
import org.json4s.JsonDSL.string2jvalue
import org.scalatest.Finders
import org.scalatest.Matchers
import org.scalatest.WordSpec

// scalastyle:off magic.number multiple.string.literals
class ArrayInsertMoveTFSpec
    extends WordSpec
    with Matchers {

  val Path = List(1, 2)

  "A ArrayInsertMoveTF" when {
    "tranforming a server insert against a client forward move" must {

      /**
       * A-IM-1
       */
      "increment the from and to indices of the move and not transform the insert if the insert is before the move." in {
        val s = ArrayInsertOperation(Path, false, 2, "X")
        val c = ArrayMoveOperation(Path, false, 3, 5)

        val (s1, c1) = ArrayInsertMoveTF.transform(s, c)

        s1 shouldBe s
        c1 shouldBe ArrayMoveOperation(Path, false, 4, 6)
      }

      /**
       * A-IM-2
       */
      "increment the from and to indices of the move and not transform the insert, if the insert is at the start of the move" in {
        val s = ArrayInsertOperation(Path, false, 3, "X")
        val c = ArrayMoveOperation(Path, false, 3, 5)

        val (s1, c1) = ArrayInsertMoveTF.transform(s, c)

        s1 shouldBe s
        c1 shouldBe ArrayMoveOperation(Path, false, 4, 6)
      }

      /**
       * A-IM-3
       */
      "increment the toIndex of the move and decrement the insert index, if the insert in the middle of the move" in {
        val s = ArrayInsertOperation(Path, false, 4, "X")
        val c = ArrayMoveOperation(Path, false, 3, 5)

        val (s1, c1) = ArrayInsertMoveTF.transform(s, c)

        s1 shouldBe ArrayInsertOperation(Path, false, 3, "X")
        c1 shouldBe ArrayMoveOperation(Path, false, 3, 6)
      }

      /**
       * A-IM-4
       */
      "increment the to index of the move and decrement the insert index, if the insert is at the end of the move" in {
        val s = ArrayInsertOperation(Path, false, 5, "X")
        val c = ArrayMoveOperation(Path, false, 3, 5)

        val (s1, c1) = ArrayInsertMoveTF.transform(s, c)

        s1 shouldBe ArrayInsertOperation(Path, false, 4, "X")
        c1 shouldBe ArrayMoveOperation(Path, false, 3, 6)
      }

      /**
       * A-IM-5
       */
      "transform neither operation, if the insert is after the move" in {
        val s = ArrayInsertOperation(Path, false, 6, "X")
        val c = ArrayMoveOperation(Path, false, 3, 5)

        val (s1, c1) = ArrayInsertMoveTF.transform(s, c)

        s1 shouldBe s
        c1 shouldBe c
      }
    }

    "tranforming a insert against a backward move" must {

      /**
       * A-IM-6
       */
      "increment the from and to indices of the move and not transform the insert if the insert is before the move." in {
        val s = ArrayInsertOperation(Path, false, 2, "X")
        val c = ArrayMoveOperation(Path, false, 5, 3)

        val (s1, c1) = ArrayInsertMoveTF.transform(s, c)

        s1 shouldBe s
        c1 shouldBe ArrayMoveOperation(Path, false, 6, 4)
      }

      /**
       * A-IM-7
       */
      "increment the from index of the move and increment the insert, if the insert is at the start of the move" in {
        val s = ArrayInsertOperation(Path, false, 3, "X")
        val c = ArrayMoveOperation(Path, false, 5, 3)

        val (s1, c1) = ArrayInsertMoveTF.transform(s, c)

        s1 shouldBe s
        c1 shouldBe ArrayMoveOperation(Path, false, 6, 4)
      }

      /**
       * A-IM-8
       */
      "increment the from index of the move and increment the insert, if the insert in the middle of the move" in {
        val s = ArrayInsertOperation(Path, false, 4, "X")
        val c = ArrayMoveOperation(Path, false, 5, 3)

        val (s1, c1) = ArrayInsertMoveTF.transform(s, c)

        s1 shouldBe ArrayInsertOperation(Path, false, 5, "X")
        c1 shouldBe ArrayMoveOperation(Path, false, 6, 3)
      }

      /**
       * A-IM-9
       */
      "increment the to index of the move and increment the insert index, if the insert is at the end of the move" in {
        val s = ArrayInsertOperation(Path, false, 5, "X")
        val c = ArrayMoveOperation(Path, false, 5, 3)

        val (s1, c1) = ArrayInsertMoveTF.transform(s, c)

        s1 shouldBe ArrayInsertOperation(Path, false, 6, "X")
        c1 shouldBe ArrayMoveOperation(Path, false, 6, 3)
      }

      /**
       * A-IM-10
       */
      "transform neither operation if the insert is after the move" in {
        val s = ArrayInsertOperation(Path, false, 6, "X")
        val c = ArrayMoveOperation(Path, false, 5, 3)

        val (s1, c1) = ArrayInsertMoveTF.transform(s, c)

        s1 shouldBe s
        c1 shouldBe c
      }
    }

    "tranforming an insert against an identity move" must {

      /**
       * A-IM-11
       */
      "increment the from and to indices of the move and not transform the insert if the insert is before the move." in {
        val s = ArrayInsertOperation(Path, false, 1, "X")
        val c = ArrayMoveOperation(Path, false, 3, 3)

        val (s1, c1) = ArrayInsertMoveTF.transform(s, c)

        s1 shouldBe s
        c1 shouldBe ArrayMoveOperation(Path, false, 4, 4)
      }

      /**
       * A-IM-12
       */
      "increment the from and to indices of the move and not transform the insert, if the insert is at the start of the move" in {
        val s = ArrayInsertOperation(Path, false, 3, "X")
        val c = ArrayMoveOperation(Path, false, 3, 3)

        val (s1, c1) = ArrayInsertMoveTF.transform(s, c)

        s1 shouldBe s
        c1 shouldBe ArrayMoveOperation(Path, false, 4, 4)
      }

      /**
       * A-IM-13
       */
      "transform neither operaiton, if the insert is after the move" in {
        val s = ArrayInsertOperation(Path, false, 4, "X")
        val c = ArrayMoveOperation(Path, false, 3, 3)

        val (s1, c1) = ArrayInsertMoveTF.transform(s, c)

        s1 shouldBe s
        c1 shouldBe c
      }
    }
  }
}
