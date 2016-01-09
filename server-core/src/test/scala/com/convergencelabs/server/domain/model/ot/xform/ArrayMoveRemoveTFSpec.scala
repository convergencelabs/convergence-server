package com.convergencelabs.server.domain.model.ot

import org.json4s.JsonAST.JString
import org.scalatest.Finders
import org.scalatest.Matchers
import org.scalatest.WordSpec

// scalastyle:off magic.number
class ArrayMoveRemoveTFSpec
    extends WordSpec
    with Matchers {

  val Path = List(1, 2)

  "A ArrayMoveRemoveTF" when {
    "tranforming an forward move against a remove" must {

      /**
       * A-MR-1
       */
      "decrement the from and to indices of the move and not transform the remove if the remove is before the move." in {
        val s = ArrayMoveOperation(Path, false, 3, 5)
        val c = ArrayRemoveOperation(Path, false, 2)

        val (s1, c1) = ArrayMoveRemoveTF.transform(s, c)

        s1 shouldBe ArrayMoveOperation(Path, false, 2, 4)
        c1 shouldBe c
      }

      /**
       * A-MR-2
       */
      "noOp the server move and set the client remove index to the server move toIndex, if the remove is at the start of the move" in {
        val s = ArrayMoveOperation(Path, false, 3, 5)
        val c = ArrayRemoveOperation(Path, false, 3)

        val (s1, c1) = ArrayMoveRemoveTF.transform(s, c)

        s1 shouldBe ArrayMoveOperation(Path, true, 3, 5)
        c1 shouldBe ArrayRemoveOperation(Path, false, 5)
      }

      /**
       * A-MR-3
       */
      "decrement the server move toIndex and decrement the remove index, if the remove in the middle of the move" in {
        val s = ArrayMoveOperation(Path, false, 3, 5)
        val c = ArrayRemoveOperation(Path, false, 4)

        val (s1, c1) = ArrayMoveRemoveTF.transform(s, c)

        s1 shouldBe ArrayMoveOperation(Path, false, 3, 4)
        c1 shouldBe ArrayRemoveOperation(Path, false, 3)
      }

      /**
       * A-MR-4
       */
      "decrement the server move toIndex and decrement the remove index, if the remove is at the end of the move" in {
        val s = ArrayMoveOperation(Path, false, 3, 5)
        val c = ArrayRemoveOperation(Path, false, 5)

        val (s1, c1) = ArrayMoveRemoveTF.transform(s, c)

        s1 shouldBe ArrayMoveOperation(Path, false, 3, 4)
        c1 shouldBe ArrayRemoveOperation(Path, false, 4)
      }

      /**
       * A-MR-5
       */
      "transform neither operation, if the remove is after the move" in {
        val s = ArrayMoveOperation(Path, false, 3, 5)
        val c = ArrayRemoveOperation(Path, false, 6)

        val (s1, c1) = ArrayMoveRemoveTF.transform(s, c)

        s1 shouldBe s
        c1 shouldBe c
      }
    }

    "tranforming a backward move against a remove" must {

      /**
       * A-MR-6
       */
      "decrement the from and to indices of the move and not transform the remove, if the remove is before the move." in {
        val s = ArrayMoveOperation(Path, false, 5, 3)
        val c = ArrayRemoveOperation(Path, false, 2)

        val (s1, c1) = ArrayMoveRemoveTF.transform(s, c)

        s1 shouldBe ArrayMoveOperation(Path, false, 4, 2)
        c1 shouldBe c
      }

      /**
       * A-MR-7
       */
      "decrement the from index of the move and increment the remove, if the remove is at the start of the move" in {
        val s = ArrayMoveOperation(Path, false, 5, 3)
        val c = ArrayRemoveOperation(Path, false, 3)

        val (s1, c1) = ArrayMoveRemoveTF.transform(s, c)

        s1 shouldBe ArrayMoveOperation(Path, false, 4, 3)
        c1 shouldBe ArrayRemoveOperation(Path, false, 4)
      }

      /**
       * A-MR-8
       */
      "decrement the from index of the move and increment the remove, if the remove in the middle of the move" in {
        val s = ArrayMoveOperation(Path, false, 5, 3)
        val c = ArrayRemoveOperation(Path, false, 4)

        val (s1, c1) = ArrayMoveRemoveTF.transform(s, c)

        s1 shouldBe ArrayMoveOperation(Path, false, 4, 3)
        c1 shouldBe ArrayRemoveOperation(Path, false, 5)
      }

      /**
       * A-MR-9
       */
      "noOp the move and set the remove index to the move's toIndex, if the remove is at the end of the move" in {
        val s = ArrayMoveOperation(Path, false, 5, 3)
        val c = ArrayRemoveOperation(Path, false, 5)

        val (s1, c1) = ArrayMoveRemoveTF.transform(s, c)

        s1 shouldBe ArrayMoveOperation(Path, true, 5, 3)
        c1 shouldBe ArrayRemoveOperation(Path, false, 3)
      }

      /**
       * A-MR-10
       */
      "transform neither operation if the remove is after the move" in {
        val s = ArrayMoveOperation(Path, false, 5, 3)
        val c = ArrayRemoveOperation(Path, false, 6)

        val (s1, c1) = ArrayMoveRemoveTF.transform(s, c)

        s1 shouldBe s
        c1 shouldBe c
      }
    }

    "tranforming a identity move against a remove" must {

      /**
       * A-MR-11
       */
      "decrement the from and to indices of the move and not transform the remove if the remove is before the move." in {
        val s = ArrayMoveOperation(Path, false, 4, 4)
        val c = ArrayRemoveOperation(Path, false, 3)

        val (s1, c1) = ArrayMoveRemoveTF.transform(s, c)

        s1 shouldBe ArrayMoveOperation(Path, false, 3, 3)
        c1 shouldBe c
      }

      /**
       * A-MR-12
       */
      "noOp the move, and do not transform the remove, if the remove is at the start of the move" in {
        val s = ArrayMoveOperation(Path, false, 4, 4)
        val c = ArrayRemoveOperation(Path, false, 4)

        val (s1, c1) = ArrayMoveRemoveTF.transform(s, c)

        s1 shouldBe ArrayMoveOperation(Path, true, 4, 4)
        c1 shouldBe c
      }

      /**
       * A-MR-13
       */
      "transform neither operaiton, if the remove is after the move" in {
        val s = ArrayMoveOperation(Path, false, 4, 4)
        val c = ArrayRemoveOperation(Path, false, 5)

        val (s1, c1) = ArrayMoveRemoveTF.transform(s, c)

        s1 shouldBe s
        c1 shouldBe c
      }
    }
  }
}
