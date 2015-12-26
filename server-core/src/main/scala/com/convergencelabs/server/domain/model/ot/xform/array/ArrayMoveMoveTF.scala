package com.convergencelabs.server.domain.model.ot

import RangeRangeRelationship._
import MoveDirection._

// scalastyle:off cyclomatic.complexity
private[ot] object ArrayMoveMoveTF extends OperationTransformationFunction[ArrayMoveOperation, ArrayMoveOperation] {
  def transform(s: ArrayMoveOperation, c: ArrayMoveOperation): (ArrayMoveOperation, ArrayMoveOperation) = {
    val sMoveType = ArrayMoveRangeHelper.getMoveDirection(s)
    val cMoveType = ArrayMoveRangeHelper.getMoveDirection(c)

    (sMoveType, cMoveType) match {
      case (Forward, Forward) => transformServerForwardMoveWithClientForwardMove(s, c)
      case (Forward, Backward) => transofrmServerForwardMoveWithClientBackwardMove(s, c)
      case (Backward, Forward) => transformServerBackwardMoveWithClientForwardMove(s, c)
      case (Backward, Backward) => transformServerBackwardMoveWithClientBackwardMove(s, c)
      case (Identity, _) => (s, c)
      case (_, Identity) => (s, c)
    }
  }

  def transformServerForwardMoveWithClientForwardMove(s: ArrayMoveOperation, c: ArrayMoveOperation): (ArrayMoveOperation, ArrayMoveOperation) = {
    ArrayMoveRangeHelper.getRangeRelationship(s, c) match {
      case Precedes =>
        // A-MM-FF-1
        (s, c)
      case PrecededBy =>
        // A-MM-FF-2
        (s, c)
      case Meets =>
        // A-MM-FF-3
        (s.copy(toIndex = s.toIndex - 1), c.copy(fromIndex = c.fromIndex - 1))
      case MetBy =>
        // A-MM-FF-4
        (s.copy(fromIndex = s.fromIndex - 1), c.copy(toIndex = c.toIndex - 1))
      case Overlaps =>
        // A-MM-FF-5
        (s.copy(toIndex = s.toIndex - 1), c.copy(fromIndex = c.fromIndex - 1))
      case OverlappedBy =>
        // A-MM-FF-6
        (s.copy(fromIndex = s.fromIndex - 1), c.copy(toIndex = c.toIndex - 1))
      case Starts =>
        // A-MM-FF-7
        (s.copy(fromIndex = c.toIndex), c.copy(noOp = true))
      case StartedBy =>
        // A-MM-FF-8
        (s.copy(fromIndex = c.toIndex), c.copy(noOp = true))
      case Contains =>
        // A-MM-FF-9
        (s, c.copy(fromIndex = c.fromIndex - 1, toIndex = c.toIndex - 1))
      case ContainedBy =>
        // A-MM-FF-10
        (s.copy(fromIndex = s.fromIndex - 1, toIndex = s.toIndex - 1), c)
      case Finishes =>
        // A-MM-FF-11
        (s.copy(fromIndex = s.fromIndex - 1), c.copy(toIndex = c.toIndex - 1))
      case FinishedBy =>
        // A-MM-FF-12
        (s, c.copy(fromIndex = c.fromIndex - 1, toIndex = c.toIndex - 1))
      case EqualTo =>
        // A-MM-FF-13
        (s.copy(noOp = true), c.copy(noOp = true))
    }
  }

  def transofrmServerForwardMoveWithClientBackwardMove(s: ArrayMoveOperation, c: ArrayMoveOperation): (ArrayMoveOperation, ArrayMoveOperation) = {
    ArrayMoveRangeHelper.getRangeRelationship(s, c) match {
      case Precedes =>
        // A-MM-FB-1
        (s, c)
      case PrecededBy =>
        // A-MM-FB-2
        (s, c)
      case Meets =>
        // A-MM-FB-3
        (s.copy(toIndex = s.toIndex + 1), c.copy(toIndex = c.toIndex - 1))
      case MetBy =>
        // A-MM-FB-4
        (s.copy(fromIndex = c.toIndex), c.copy(noOp = true))
      case Overlaps =>
        // A-MM-FB-5
        (s.copy(toIndex = s.toIndex + 1), c.copy(toIndex = c.toIndex - 1))
      case OverlappedBy =>
        // A-MM-FB-6
        (s.copy(fromIndex = s.fromIndex + 1), c.copy(fromIndex = c.fromIndex - 1))
      case Starts =>
        // A-MM-FB-7
        (s.copy(fromIndex = s.fromIndex + 1, toIndex = s.toIndex + 1), c)
      case StartedBy =>
        // A-MM-FB-8
        (s.copy(fromIndex = s.fromIndex + 1), c.copy(fromIndex = c.fromIndex - 1))
      case Contains =>
        // A-MM-FB-9
        (s, c.copy(fromIndex = c.fromIndex - 1, toIndex = c.toIndex - 1))
      case ContainedBy =>
        // A-MM-FB-10
        (s.copy(fromIndex = s.fromIndex + 1, toIndex = s.toIndex + 1), c)
      case Finishes =>
        // A-MM-FB-11
        (s.copy(fromIndex = s.fromIndex + 1), c.copy(fromIndex = c.fromIndex - 1))
      case FinishedBy =>
        // A-MM-FB-12
        (s, c.copy(fromIndex = c.fromIndex - 1, toIndex = c.toIndex - 1))
      case EqualTo =>
        // A-MM-FB-13
        (s.copy(fromIndex = s.fromIndex + 1), c.copy(fromIndex = c.fromIndex - 1))
    }
  }

  def transformServerBackwardMoveWithClientForwardMove(s: ArrayMoveOperation, c: ArrayMoveOperation): (ArrayMoveOperation, ArrayMoveOperation) = {
    ArrayMoveRangeHelper.getRangeRelationship(s, c) match {
      case Precedes =>
        // A-MM-BF-1
        (s, c)
      case PrecededBy =>
        // A-MM-BF-2
        (s, c)
      case Meets =>
        // A-MM-BF-3
        (s.copy(fromIndex = c.toIndex), c.copy(noOp = true))
      case MetBy =>
        // A-MM-BF-4
        (s.copy(toIndex  = s.toIndex - 1), c.copy(toIndex = c.toIndex + 1))
      case Overlaps =>
        // A-MM-BF-5
        (s.copy(fromIndex = s.fromIndex - 1), c.copy(fromIndex = c.fromIndex + 1))
      case OverlappedBy =>
        // A-MM-BF-6
        (s.copy(toIndex = s.toIndex - 1), c.copy(toIndex = c.toIndex + 1))
      case Starts =>
        // A-MM-BF-7
        (s.copy(fromIndex = s.fromIndex - 1), c.copy(fromIndex = c.fromIndex + 1))
      case StartedBy =>
        // A-MM-BF-8
        (s, c.copy(fromIndex = c.fromIndex + 1, toIndex = c.toIndex + 1))
      case Contains =>
        // A-MM-BF-9
        (s, c.copy(fromIndex = c.fromIndex + 1, toIndex = c.toIndex + 1))
      case ContainedBy =>
        // A-MM-BF-10
        (s.copy(fromIndex = s.fromIndex - 1, toIndex = s.toIndex - 1), c)
      case Finishes =>
        // A-MM-BF-11
        (s.copy(fromIndex = s.fromIndex - 1, toIndex = s.toIndex - 1), c)
      case FinishedBy =>
        // A-MM-BF-12
        (s.copy(fromIndex = s.fromIndex - 1), c.copy(fromIndex = c.fromIndex + 1))
      case EqualTo =>
        // A-MM-BF-13
        (s.copy(fromIndex = s.fromIndex - 1), c.copy(fromIndex = c.fromIndex + 1))
    }
  }

  def transformServerBackwardMoveWithClientBackwardMove(s: ArrayMoveOperation, c: ArrayMoveOperation): (ArrayMoveOperation, ArrayMoveOperation) = {
    ArrayMoveRangeHelper.getRangeRelationship(s, c) match {
      case Precedes =>
        // A-MM-BB-1
        (s, c)
      case PrecededBy =>
        // A-MM-BB-2
        (s, c)
      case Meets =>
        // A-MM-BB-3
        (s.copy(fromIndex = s.fromIndex + 1), c.copy(toIndex = c.toIndex + 1))
      case MetBy =>
        // A-MM-BB-4
        (s.copy(toIndex = s.toIndex + 1), c.copy(fromIndex = c.fromIndex + 1))
      case Overlaps =>
        // A-MM-BB-5
        (s.copy(fromIndex = s.fromIndex + 1), c.copy(toIndex = c.toIndex + 1))
      case OverlappedBy =>
        // A-MM-BB-6
        (s.copy(toIndex = s.toIndex + 1), c.copy(fromIndex = c.fromIndex + 1))
      case Starts =>
        // A-MM-BB-7
        (s.copy(fromIndex = s.fromIndex + 1, toIndex = s.toIndex + 1), c)
      case StartedBy =>
        // A-MM-BB-8
        (s, c.copy(fromIndex = c.fromIndex + 1, toIndex = c.toIndex + 1))
      case Contains =>
        // A-MM-BB-9
        (s, c.copy(fromIndex = c.fromIndex + 1, toIndex = c.toIndex + 1))
      case ContainedBy =>
        // A-MM-BB-10
        (s.copy(fromIndex = s.fromIndex + 1, toIndex = s.toIndex + 1), c)
      case Finishes =>
        // A-MM-BB-11
        (s.copy(fromIndex = c.toIndex), c.copy(noOp = true))
      case FinishedBy =>
        // A-MM-BB-12
        (s.copy(fromIndex = c.toIndex), c.copy(noOp = true))
      case EqualTo =>
        // A-MM-BB-13
        (s.copy(noOp = true), c.copy(noOp = true))
    }
  }
}
