package com.convergencelabs.server.domain.model.ot

import org.scalatest.WordSpec
import org.scalatest.Matchers

class ArrayMoveRangeHelperSpec extends WordSpec with Matchers {

  "A ArrayMoveRangeHelper" when {

    "determining the range relationship of two move operations" must {
      "return Precedes when the server move precedes the client move" in {
        val c = MoveMoveCase.Precedes
        ArrayMoveRangeHelper.getRangeRelationship(c.op1, c.op2) shouldBe RangeRangeRelationship.Precedes
      }

      "return PrecededBy when the server move is preceded by the client move" in {
        val c = MoveMoveCase.PrecededBy
        ArrayMoveRangeHelper.getRangeRelationship(c.op1, c.op2) shouldBe RangeRangeRelationship.PrecededBy
      }

      "return Meets when the server move meets the client move" in {
        val c = MoveMoveCase.Meets
        ArrayMoveRangeHelper.getRangeRelationship(c.op1, c.op2) shouldBe RangeRangeRelationship.Meets
      }

      "return MetBy when the server move is met  by the client move" in {
        val c = MoveMoveCase.MetBy
        ArrayMoveRangeHelper.getRangeRelationship(c.op1, c.op2) shouldBe RangeRangeRelationship.MetBy
      }

      "return Overlaps when the server move overlaps the client move" in {
        val c = MoveMoveCase.Overlaps
        ArrayMoveRangeHelper.getRangeRelationship(c.op1, c.op2) shouldBe RangeRangeRelationship.Overlaps
      }

      "return OverlappedBy when the server move is overlapped by the client move" in {
        val c = MoveMoveCase.OverlappedBy
        ArrayMoveRangeHelper.getRangeRelationship(c.op1, c.op2) shouldBe RangeRangeRelationship.OverlappedBy
      }

      "return Contains when the server move contains the client move" in {
        val c = MoveMoveCase.Contains
        ArrayMoveRangeHelper.getRangeRelationship(c.op1, c.op2) shouldBe RangeRangeRelationship.Contains
      }

      "return ContainedBy when the server move is contained by the client move" in {
        val c = MoveMoveCase.ContainedBy
        ArrayMoveRangeHelper.getRangeRelationship(c.op1, c.op2) shouldBe RangeRangeRelationship.ContainedBy
      }

      "return Finishes when the server move finishes the client move" in {
        val c = MoveMoveCase.Finishes
        ArrayMoveRangeHelper.getRangeRelationship(c.op1, c.op2) shouldBe RangeRangeRelationship.Finishes
      }

      "return FinishedBy when the server move is finished by the client move" in {
        val c = MoveMoveCase.FinishedBy
        ArrayMoveRangeHelper.getRangeRelationship(c.op1, c.op2) shouldBe RangeRangeRelationship.FinishedBy
      }

      "return EqualTo when the server move is equal to the client move" in {
        val c = MoveMoveCase.EqualTo
        ArrayMoveRangeHelper.getRangeRelationship(c.op1, c.op2) shouldBe RangeRangeRelationship.EqualTo
      }
    }

    "evaluating move direction" must {
      "return Forward for a forward move" in {
        val op = ArrayMoveOperation(List(), false, 1, 2)
        ArrayMoveRangeHelper.getMoveDirection(op) shouldBe MoveDirection.Forward
      }
      
      "return Backward for a forward move" in {
        val op = ArrayMoveOperation(List(), false, 2, 1)
        ArrayMoveRangeHelper.getMoveDirection(op) shouldBe MoveDirection.Backward
      }
      
      "return Identity for a forward move" in {
        val op = ArrayMoveOperation(List(), false, 2, 2)
        ArrayMoveRangeHelper.getMoveDirection(op) shouldBe MoveDirection.Identity
      }
    }

    "evaluating if a move is of a particular direction" must {
      "identify a forward move as a forward move" in {
        val op = ArrayMoveOperation(List(), false, 1, 2)
        ArrayMoveRangeHelper.isForwardMove(op) shouldBe true
      }

      "identify a backward move as not a forward move" in {
        val op = ArrayMoveOperation(List(), false, 2, 1)
        ArrayMoveRangeHelper.isForwardMove(op) shouldBe false
      }

      "identify a backward move as a backward move" in {
        val op = ArrayMoveOperation(List(), false, 2, 1)
        ArrayMoveRangeHelper.isBackwardMoveMove(op) shouldBe true
      }

      "identify a forward move as not a backward move" in {
        val op = ArrayMoveOperation(List(), false, 1, 2)
        ArrayMoveRangeHelper.isBackwardMoveMove(op) shouldBe false
      }

      "identify an identity move as an idendity move" in {
        val op = ArrayMoveOperation(List(), false, 1, 1)
        ArrayMoveRangeHelper.isIdentityMove(op) shouldBe true
      }

      "identify a non identity move as not an identity move" in {
        val op = ArrayMoveOperation(List(), false, 2, 1)
        ArrayMoveRangeHelper.isIdentityMove(op) shouldBe false
      }
    }

    "checking if an index is after a range" must {
      "return true if the index is after the range of a forward move" in {
        val op = ArrayMoveOperation(List(), false, 1, 2)
        ArrayMoveRangeHelper.indexAfterRange(op, 3) shouldBe true
      }

      "return true if the index is after the range of a backward move" in {
        val op = ArrayMoveOperation(List(), false, 2, 1)
        ArrayMoveRangeHelper.indexAfterRange(op, 3) shouldBe true
      }

      "return true if the index is after the range of an dentity move" in {
        val op = ArrayMoveOperation(List(), false, 2, 2)
        ArrayMoveRangeHelper.indexAfterRange(op, 3) shouldBe true
      }
    }

    "checking if an index is before a range" must {
      "return true if the index is before the range of a forward move" in {
        val op = ArrayMoveOperation(List(), false, 2, 3)
        ArrayMoveRangeHelper.indexBeforeRange(op, 1) shouldBe true
      }

      "return true if the index is before the range of a backward move" in {
        val op = ArrayMoveOperation(List(), false, 3, 2)
        ArrayMoveRangeHelper.indexBeforeRange(op, 1) shouldBe true
      }

      "return true if the index is before the range of an identity move" in {
        val op = ArrayMoveOperation(List(), false, 2, 2)
        ArrayMoveRangeHelper.indexBeforeRange(op, 1) shouldBe true
      }
    }

    "checking if an index is within a range" must {
      "return true if the index is before the range" in {
        val op = ArrayMoveOperation(List(), false, 2, 4)
        ArrayMoveRangeHelper.indexWithinRange(op, 1) shouldBe false
      }

      "return true if the index is at the lower end of the range" in {
        val op = ArrayMoveOperation(List(), false, 2, 4)
        ArrayMoveRangeHelper.indexWithinRange(op, 2) shouldBe false
      }

      "return true if the index is within the range" in {
        val op = ArrayMoveOperation(List(), false, 2, 4)
        ArrayMoveRangeHelper.indexWithinRange(op, 3) shouldBe true
      }

      "return true if the index is at the upper end of the range" in {
        val op = ArrayMoveOperation(List(), false, 2, 4)
        ArrayMoveRangeHelper.indexWithinRange(op, 4) shouldBe false
      }

      "return true if the index is after the range" in {
        val op = ArrayMoveOperation(List(), false, 2, 4)
        ArrayMoveRangeHelper.indexWithinRange(op, 5) shouldBe false
      }
    }
  }

  def validateRangeRelationshipType(c: Case, expected: RangeRangeRelationship.Value) {
    ArrayMoveRangeHelper.getRangeRelationship(c.op1, c.op2) shouldBe expected
  }
}

case class Case(op1From: Int, op1To: Int, op2From: Int, op2To: Int) {
  val op1 = ArrayMoveOperation(List(), false, op1From, op1To)
  val op2 = ArrayMoveOperation(List(), false, op2From, op2To)
}

object MoveMoveCase {

  // Each comment below represents an array with 6 elements.
  // a "." is a non-move index, a "|" is a move index

  // op1 |.|...
  // op2 ...|.|
  val Precedes = Case(0, 2, 3, 5)

  // op1 ...|.|
  // op2 |.|...
  val PrecededBy = Case(3, 5, 0, 2)

  // op1 |.|...
  // op2 ..|..|
  val Meets = Case(0, 2, 2, 5)

  // op1 ..|..|
  // op2 |.|...
  val MetBy = Case(2, 5, 0, 2)

  // op1 |..|..
  // op2 ..|..|
  val Overlaps = Case(0, 3, 2, 5)

  // op1 ..|..|
  // op2 |..|..
  val OverlappedBy = Case(2, 5, 0, 3)

  // op1 |..|..
  // op2 |....|
  val Starts = Case(0, 3, 0, 5)

  // op1 |....|
  // op2 |..|..
  val StartedBy = Case(0, 5, 0, 3)

  // op1 |..|..
  // op2 |..|..
  val EqualTo = Case(0, 3, 0, 3)

  // op1 |....|
  // op2 .|..|.
  val Contains = Case(0, 5, 1, 4)

  // op1 .|..|.
  // op2 |....|
  val ContainedBy = Case(1, 4, 0, 5)

  // op1 ...|.|
  // op2 |....|
  val Finishes = Case(3, 5, 0, 5)

  // op1 |....|
  // op2 ...|.|
  val FinishedBy = Case(0, 5, 3, 5)

  val cases = Map(
    "Preceeds" -> Precedes,
    "PreceededBy" -> PrecededBy,
    "Meets" -> Meets,
    "MetBy" -> MetBy,
    "Overlaps" -> Overlaps,
    "OverlappedBy" -> OverlappedBy,
    "EqualTo" -> EqualTo,
    "Contains" -> Contains,
    "ContainedBy" -> ContainedBy,
    "Starts" -> Starts,
    "StartedBy" -> StartedBy,
    "Finshes" -> Finishes,
    "FinishedBy" -> FinishedBy)
}