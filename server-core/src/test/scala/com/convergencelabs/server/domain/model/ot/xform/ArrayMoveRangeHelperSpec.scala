package com.convergencelabs.server.domain.model.ot

import org.scalatest.WordSpec
import org.scalatest.Matchers

class ArrayMoveRangeHelperSpec extends WordSpec with Matchers {

  "A ArrayMoveRangeHelper" when {

    "determining the range relationship of two move operations" must {
      "return Precedes when the server move precedes the client move" in {
        val c = MoveMoveCase.Precedes
        ArrayMoveRangeHelper.getRangeRelationship(c.op1, c.op2) shouldBe RangeRelationship.Precedes
      }

      "return PrecededBy when the server move is preceded by the client move" in {
        val c = MoveMoveCase.PrecededBy
        ArrayMoveRangeHelper.getRangeRelationship(c.op1, c.op2) shouldBe RangeRelationship.PrecededBy
      }

      "return Meets when the server move meets the client move" in {
        val c = MoveMoveCase.Meets
        ArrayMoveRangeHelper.getRangeRelationship(c.op1, c.op2) shouldBe RangeRelationship.Meets
      }

      "return MetBy when the server move is met  by the client move" in {
        val c = MoveMoveCase.MetBy
        ArrayMoveRangeHelper.getRangeRelationship(c.op1, c.op2) shouldBe RangeRelationship.MetBy
      }

      "return Overlaps when the server move overlaps the client move" in {
        val c = MoveMoveCase.Overlaps
        ArrayMoveRangeHelper.getRangeRelationship(c.op1, c.op2) shouldBe RangeRelationship.Overlaps
      }

      "return OverlappedBy when the server move is overlapped by the client move" in {
        val c = MoveMoveCase.OverlappedBy
        ArrayMoveRangeHelper.getRangeRelationship(c.op1, c.op2) shouldBe RangeRelationship.OverlappedBy
      }

      "return Contains when the server move contains the client move" in {
        val c = MoveMoveCase.Contains
        ArrayMoveRangeHelper.getRangeRelationship(c.op1, c.op2) shouldBe RangeRelationship.Contains
      }

      "return ContainedBy when the server move is contained by the client move" in {
        val c = MoveMoveCase.ContainedBy
        ArrayMoveRangeHelper.getRangeRelationship(c.op1, c.op2) shouldBe RangeRelationship.ContainedBy
      }

      "return Finishes when the server move finishes the client move" in {
        val c = MoveMoveCase.Finishes
        ArrayMoveRangeHelper.getRangeRelationship(c.op1, c.op2) shouldBe RangeRelationship.Finishes
      }

      "return FinishedBy when the server move is finished by the client move" in {
        val c = MoveMoveCase.FinishedBy
        ArrayMoveRangeHelper.getRangeRelationship(c.op1, c.op2) shouldBe RangeRelationship.FinishedBy
      }

      "return EqualTo when the server move is equal to the client move" in {
        val c = MoveMoveCase.EqualTo
        ArrayMoveRangeHelper.getRangeRelationship(c.op1, c.op2) shouldBe RangeRelationship.EqualTo
      }
    }

    "determining if a range relationship is of a particular type for two move operations" must {
      "only return true for precedes when the first range precedes the second" in {
        validateMoveMoveFunction(MoveMoveCase.Precedes, ArrayMoveRangeHelper.precedes, "precedes")
      }

      "only return true for precededBy when the first range is preceded by the second" in {
        validateMoveMoveFunction(MoveMoveCase.PrecededBy, ArrayMoveRangeHelper.precededBy, "precededBy")
      }

      "only return true for meets when the first range meets the second" in {
        validateMoveMoveFunction(MoveMoveCase.Meets, ArrayMoveRangeHelper.meets, "meets")
      }

      "only return true for metBy when the first range is met by the second" in {
        validateMoveMoveFunction(MoveMoveCase.MetBy, ArrayMoveRangeHelper.metBy, "metBy")
      }

      "only return true for overlaps when the first range overlaps the second" in {
        validateMoveMoveFunction(MoveMoveCase.Overlaps, ArrayMoveRangeHelper.overlaps, "overlaps")
      }

      "only return true for overlappedBy when the first range is overlapped by the second" in {
        validateMoveMoveFunction(MoveMoveCase.OverlappedBy, ArrayMoveRangeHelper.overlappedBy, "overlappedBy")
      }

      "only return true for contains when the first range contains the second" in {
        validateMoveMoveFunction(MoveMoveCase.Contains, ArrayMoveRangeHelper.contains, "contains")
      }

      "only return true for containedBy when the first range is contained by the second" in {
        validateMoveMoveFunction(MoveMoveCase.ContainedBy, ArrayMoveRangeHelper.containedBy, "containedBy")
      }

      "only return true for starts when the first range starts the second" in {
        validateMoveMoveFunction(MoveMoveCase.Starts, ArrayMoveRangeHelper.starts, "starts")
      }

      "only return true for startedBy when the first range is started by the second" in {
        validateMoveMoveFunction(MoveMoveCase.StartedBy, ArrayMoveRangeHelper.startedBy, "startedBy")
      }

      "only return true for finishes when the first range finishes the second" in {
        validateMoveMoveFunction(MoveMoveCase.Finishes, ArrayMoveRangeHelper.finishes, "finishes")
      }

      "only return true for finishedBy when the first range is started by the second" in {
        validateMoveMoveFunction(MoveMoveCase.FinishedBy, ArrayMoveRangeHelper.finishedBy, "finishedBy")
      }

      "only return true for equalTo when the first range is equal to the second" in {
        validateMoveMoveFunction(MoveMoveCase.EqualTo, ArrayMoveRangeHelper.equalTo, "equalTo")
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

  type MoveMoveFunction = Function2[ArrayMoveOperation, ArrayMoveOperation, Boolean]

  def validateMoveMoveFunction(expected: Case, f: MoveMoveFunction, fName: String): Unit = {
    MoveMoveCase.cases.foreach {
      case (n, c) =>
        if (c == expected && !f(c.op1, c.op2)) {
          fail(s"Case ${n} should return true for function $fName, but returned false")
        } else if (c != expected && f(c.op1, c.op2)) {
          fail(s"Case ${n} should return false for function $fName, but returned true")
        }
    }
  }

  def validateRangeRelationshipType(c: Case, expected: RangeRelationship.Value) {
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