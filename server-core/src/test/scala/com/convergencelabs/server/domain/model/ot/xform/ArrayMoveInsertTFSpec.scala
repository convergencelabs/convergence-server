package com.convergencelabs.server.domain.model.ot

import org.json4s.JsonAST.JString
import org.json4s.JsonDSL.string2jvalue
import org.scalatest.Finders
import org.scalatest.Matchers
import org.scalatest.WordSpec

// scalastyle:off magic.number multiple.string.literals
class ArrayMoveInsertTFSpec
    extends WordSpec
    with Matchers {

  val Path = List(1, 2)

  "A ArrayMoveInsertTF" when {
    "tranforming an forward move against an insert" must {

      /**
       * A-MI-1
       */
      "increment the from and to indices of the move and not transform the insert if the insert is before the move." in {
        val s = ArrayMoveOperation(Path, false, 3, 5)
        val c = ArrayInsertOperation(Path, false, 2, "X")

        val (s1, c1) = ArrayMoveInsertTF.transform(s, c)

        s1 shouldBe ArrayMoveOperation(Path, false, 4, 6)
        c1 shouldBe c
      }

      /**
       * A-MI-2
       */
      "increment the from and to indices of the move and not transform the insert, if the insert is at the start of the move" in {
        val s = ArrayMoveOperation(Path, false, 3, 5)
        val c = ArrayInsertOperation(Path, false, 3, "X")

        val (s1, c1) = ArrayMoveInsertTF.transform(s, c)

        s1 shouldBe ArrayMoveOperation(Path, false, 4, 6)
        c1 shouldBe c
      }

      /**
       * A-MI-3
       */
      "increment the to index of the move and decrement the insert index, if the insert in the middle of the move" in {
        val s = ArrayMoveOperation(Path, false, 3, 5)
        val c = ArrayInsertOperation(Path, false, 4, "X")

        val (s1, c1) = ArrayMoveInsertTF.transform(s, c)

        s1 shouldBe ArrayMoveOperation(Path, false, 3, 6)
        c1 shouldBe ArrayInsertOperation(Path, false, 3, "X")
      }

      /**
       * A-MI-4
       */
      "increment the to index of the move and decrement the insert index, if the insert is at the end of the move" in {
        val s = ArrayMoveOperation(Path, false, 3, 5)
        val c = ArrayInsertOperation(Path, false, 5, "X")

        val (s1, c1) = ArrayMoveInsertTF.transform(s, c)

        s1 shouldBe ArrayMoveOperation(Path, false, 3, 6)
        c1 shouldBe ArrayInsertOperation(Path, false, 4, "X")
      }

      /**
       * A-MI-5
       */
      "transform neither operation, if the insert is after the move" in {
        val s = ArrayMoveOperation(Path, false, 3, 5)
        val c = ArrayInsertOperation(Path, false, 6, "X")

        val (s1, c1) = ArrayMoveInsertTF.transform(s, c)

        s1 shouldBe s
        c1 shouldBe c
      }
    }

    "tranforming a backward move against an insert" must {

      /**
       * A-MI-6
       */
      "increment the from and to indices of the move and not transform the insert if the insert is before the move." in {
        val s = ArrayMoveOperation(Path, false, 5, 3)
        val c = ArrayInsertOperation(Path, false, 2, "X")

        val (s1, c1) = ArrayMoveInsertTF.transform(s, c)

        s1 shouldBe ArrayMoveOperation(Path, false, 6, 4)
        c1 shouldBe c
      }

      /**
       * A-MI-7
       */
      "increment the from index of the move and increment the insert, if the insert is at the start of the move" in {
        val s = ArrayMoveOperation(Path, false, 5, 3)
        val c = ArrayInsertOperation(Path, false, 3, "X")

        val (s1, c1) = ArrayMoveInsertTF.transform(s, c)

        s1 shouldBe ArrayMoveOperation(Path, false, 6, 4)
        c1 shouldBe c
      }

      /**
       * A-MI-8
       */
      "increment the from index of the move and increment the insert, if the insert in the middle of the move" in {
        val s = ArrayMoveOperation(Path, false, 5, 3)
        val c = ArrayInsertOperation(Path, false, 4, "X")

        val (s1, c1) = ArrayMoveInsertTF.transform(s, c)

        s1 shouldBe ArrayMoveOperation(Path, false, 6, 3)
        c1 shouldBe ArrayInsertOperation(Path, false, 5, "X")
      }

      /**
       * A-MI-9
       */
      "increment the to index of the move and increment the insert index, if the insert is at the end of the move" in {
        val s = ArrayMoveOperation(Path, false, 5, 3)
        val c = ArrayInsertOperation(Path, false, 5, "X")

        val (s1, c1) = ArrayMoveInsertTF.transform(s, c)

        s1 shouldBe ArrayMoveOperation(Path, false, 6, 3)
        c1 shouldBe ArrayInsertOperation(Path, false, 6, "X")
      }

      /**
       * A-MI-10
       */
      "transform neither operation if the insert is after the move" in {
        val s = ArrayMoveOperation(Path, false, 5, 3)
        val c = ArrayInsertOperation(Path, false, 6, "X")

        val (s1, c1) = ArrayMoveInsertTF.transform(s, c)

        s1 shouldBe s
        c1 shouldBe c
      }
    }

    "tranforming a identity move against an insert" must {

      /**
       * A-MI-11
       */
      "increment the from and to indices of the move and not transform the insert if the insert is before the move." in {
        val s = ArrayMoveOperation(Path, false, 3, 3)
        val c = ArrayInsertOperation(Path, false, 2, "X")

        val (s1, c1) = ArrayMoveInsertTF.transform(s, c)

        s1 shouldBe ArrayMoveOperation(Path, false, 4, 4)
        c1 shouldBe c
      }

      /**
       * A-MI-12
       */
      "increment the from and to indices of the move and not transform the insert, if the insert is at the start of the move" in {
        val s = ArrayMoveOperation(Path, false, 3, 3)
        val c = ArrayInsertOperation(Path, false, 3, "X")

        val (s1, c1) = ArrayMoveInsertTF.transform(s, c)

        s1 shouldBe ArrayMoveOperation(Path, false, 4, 4)
        c1 shouldBe c
      }

      /**
       * A-MI-13
       */
      "transform neither operaiton, if the insert is after the move" in {
        val s = ArrayMoveOperation(Path, false, 3, 3)
        val c = ArrayInsertOperation(Path, false, 4, "X")

        val (s1, c1) = ArrayMoveInsertTF.transform(s, c)

        s1 shouldBe s
        c1 shouldBe c
      }
    }
  }
}
