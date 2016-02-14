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
       * A-PM-1
       */
      "transform neither operation if the replace is before the move." in {
        val s = ArrayReplaceOperation(Path, false, 2, X)
        val c = ArrayMoveOperation(Path, false, 3, 5)

        val (s1, c1) = ArrayReplaceMoveTF.transform(s, c)

        s1 shouldBe s
        c1 shouldBe c
      }

      /**
       * A-PM-2
       */
      "no transform the move and set the replace index to the move's toIndex, if the replace is at the start of the move" in {
        val s = ArrayReplaceOperation(Path, false, 3, X)
        val c = ArrayMoveOperation(Path, false, 3, 5)

        val (s1, c1) = ArrayReplaceMoveTF.transform(s, c)

        s1 shouldBe ArrayReplaceOperation(Path, false, 5, X)
        c1 shouldBe c
      }

      /**
       * A-PM-3
       */
      "decrement the replace index and not transform the move, if the replace in the middle of the move" in {
        val s = ArrayReplaceOperation(Path, false, 4, X)
        val c = ArrayMoveOperation(Path, false, 3, 5)

        val (s1, c1) = ArrayReplaceMoveTF.transform(s, c)

        s1 shouldBe ArrayReplaceOperation(Path, false, 3, X)
        c1 shouldBe ArrayMoveOperation(Path, false, 3, 5)
      }

      /**
       * A-PM-4
       */
      "not transform the move and decrement the replace index, if the replace is at the end of the move" in {
        val s = ArrayReplaceOperation(Path, false, 5, X)
        val c = ArrayMoveOperation(Path, false, 3, 5)

        val (s1, c1) = ArrayReplaceMoveTF.transform(s, c)

        s1 shouldBe ArrayReplaceOperation(Path, false, 4, X)
        c1 shouldBe c
      }

      /**
       * A-PM-5
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
       * A-PM-6
       */
      "no transform either operation, if the replace is before the move." in {
        val s = ArrayReplaceOperation(Path, false, 2, X)
        val c = ArrayMoveOperation(Path, false, 5, 3)

        val (s1, c1) = ArrayReplaceMoveTF.transform(s, c)

        s1 shouldBe s
        c1 shouldBe c
      }

      /**
       * A-PM-7
       */
      "increment the replace and not transform the move, if the replace is at the start of the move" in {
        val s = ArrayReplaceOperation(Path, false, 3, X)
        val c = ArrayMoveOperation(Path, false, 5, 3)

        val (s1, c1) = ArrayReplaceMoveTF.transform(s, c)

        s1 shouldBe ArrayReplaceOperation(Path, false, 4, X)
        c1 shouldBe c
      }

      /**
       * A-PM-8
       */
      "transform neither operation if the replace in the middle of the move" in {
        val s = ArrayReplaceOperation(Path, false, 4, X)
        val c = ArrayMoveOperation(Path, false, 5, 3)

        val (s1, c1) = ArrayReplaceMoveTF.transform(s, c)

        s1 shouldBe ArrayReplaceOperation(Path, false, 5, X)
        c1 shouldBe c
      }

      /**
       * A-PM-9
       */
      "increment the to index of the move and increment the replace index, if the replace is at the end of the move" in {
        val s = ArrayReplaceOperation(Path, false, 5, X)
        val c = ArrayMoveOperation(Path, false, 5, 3)

        val (s1, c1) = ArrayReplaceMoveTF.transform(s, c)

        s1 shouldBe ArrayReplaceOperation(Path, false, 3, X)
        c1 shouldBe c
      }

      /**
       * A-PM-10
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
       * A-PM-11
       */
      "transform neither operation if the replace is before the move." in {
        val s = ArrayReplaceOperation(Path, false, 1, X)
        val c = ArrayMoveOperation(Path, false, 3, 3)

        val (s1, c1) = ArrayReplaceMoveTF.transform(s, c)

        s1 shouldBe s
        c1 shouldBe c
      }

      /**
       * A-PM-12
       */
      "transform neither operation, if the replace is at the start of the move" in {
        val s = ArrayReplaceOperation(Path, false, 3, X)
        val c = ArrayMoveOperation(Path, false, 3, 3)

        val (s1, c1) = ArrayReplaceMoveTF.transform(s, c)

        s1 shouldBe s
        c1 shouldBe c
      }

      /**
       * A-PM-13
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
