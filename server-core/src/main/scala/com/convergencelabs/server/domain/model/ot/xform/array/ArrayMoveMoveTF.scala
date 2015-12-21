package com.convergencelabs.server.domain.model.ot

import RangeRelationship._
import MoveDirection._

// scalastyle:off cyclomatic.complexity
private[ot] object ArrayMoveMoveTF extends OperationTransformationFunction[ArrayMoveOperation, ArrayMoveOperation] {
  def transform(s: ArrayMoveOperation, c: ArrayMoveOperation): (ArrayMoveOperation, ArrayMoveOperation) = {
    val sMoveType = ArrayMoveRangeHelper.getMoveDirection(s)
    val cMoveType = ArrayMoveRangeHelper.getMoveDirection(c)

    (sMoveType, cMoveType) match {
      case (Forward, Forward) => transformTwoForwardMoves(s, c)
      case (Forward, Backward) => transofrmServerForwardMoveWithClientBackwardMove(s, c)
      case (Backward, Forward) => transofrmBackwardMoveWithForwardMove(s, c)
      case (Backward, Backward) => transofrmBackwardMoveWithBackwardMove(s, c)
      case (Identity, _) => (s, c)
      case (_, Identity) => (s, c)
    }
  }

  def transformTwoForwardMoves(s: ArrayMoveOperation, c: ArrayMoveOperation): (ArrayMoveOperation, ArrayMoveOperation) = {
    ArrayMoveRangeHelper.getRangeRelationship(s, c) match {
      case Precedes | PrecededBy =>
        (s, c)
      case MetBy | OverlappedBy | Finishes =>
        (s.copy(fromIndex = s.fromIndex - 1), c.copy(toIndex = c.toIndex - 1))
      case ContainedBy =>
        (s.copy(fromIndex = s.fromIndex - 1, toIndex = s.toIndex - 1), c)
      case Starts | StartedBy =>
        (s.copy(fromIndex = c.toIndex), c.copy(noOp = true))
      case Contains | FinishedBy =>
        (s, c.copy(fromIndex = c.fromIndex - 1, toIndex = c.toIndex - 1))
      case Overlaps | Meets =>
        (s.copy(toIndex = s.toIndex - 1), c.copy(fromIndex = c.fromIndex - 1))
      case EqualTo =>
        (s.copy(noOp = true), c.copy(noOp = true))
    }
  }

  def transofrmBackwardMoveWithBackwardMove(s: ArrayMoveOperation, c: ArrayMoveOperation): (ArrayMoveOperation, ArrayMoveOperation) = {
    ArrayMoveRangeHelper.getRangeRelationship(s, c) match {
      case Precedes | PrecededBy =>
        (s, c)
      case Meets | Overlaps =>
        (s.copy(toIndex = s.toIndex + 1), c.copy(fromIndex = c.fromIndex + 1))
      case FinishedBy | Finishes =>
        (s.copy(fromIndex = c.toIndex), c.copy(noOp = true))
      case Contains =>
        (s.copy(fromIndex = s.fromIndex + 1, toIndex = s.toIndex + 1), c)
      case Starts | ContainedBy =>
        (s, c.copy(fromIndex = c.fromIndex + 1, toIndex = c.toIndex + 1))
      case EqualTo =>
        (s.copy(noOp = true), c.copy(noOp = true))
      case StartedBy | OverlappedBy | MetBy =>
        (s.copy(fromIndex = s.fromIndex + 1), c.copy(toIndex = c.toIndex + 1))
    }
  }

  def transofrmBackwardMoveWithForwardMove(s: ArrayMoveOperation, c: ArrayMoveOperation): (ArrayMoveOperation, ArrayMoveOperation) = {
    ArrayMoveRangeHelper.getRangeRelationship(s, c) match {
      case Precedes | PrecededBy =>
        (s, c)
      case Meets =>
        (s.copy(fromIndex = c.toIndex), c.copy(noOp = true))
      case FinishedBy | Starts | EqualTo =>
        (s.copy(fromIndex = s.fromIndex + 1), c.copy(fromIndex = c.fromIndex - 1))
      case Contains | StartedBy =>
        (s.copy(fromIndex = s.fromIndex + 1, toIndex = s.toIndex + 1), c)
      case ContainedBy | Finishes =>
        (s, c.copy(fromIndex = c.fromIndex + 1, toIndex = c.toIndex + 1))
      case OverlappedBy =>
        (s.copy(toIndex = s.toIndex + 1), c.copy(toIndex = c.toIndex - 1))
    }
  }

  def transofrmServerForwardMoveWithClientBackwardMove(s: ArrayMoveOperation, c: ArrayMoveOperation): (ArrayMoveOperation, ArrayMoveOperation) = {
    ArrayMoveRangeHelper.getRangeRelationship(s, c) match {
      case Precedes | PrecededBy =>
        (s, c)
      case Meets => //Broken
        (s.copy(toIndex = s.toIndex - 1), c.copy(toIndex = c.toIndex + 1))
      case MetBy =>
        (s.copy(fromIndex = c.toIndex), c.copy(noOp = true))
      case Overlaps =>
        (s.copy(toIndex = s.toIndex + 1), c.copy(toIndex = c.toIndex - 1))
      case OverlappedBy =>
        (s.copy(fromIndex = s.fromIndex + 1), c.copy(fromIndex = c.fromIndex - 1))
      case Starts =>
        (s.copy(fromIndex = s.fromIndex + 1, toIndex = s.toIndex + 1), c)
      case StartedBy =>
        (s.copy(fromIndex = s.fromIndex + 1), c.copy(fromIndex = c.fromIndex - 1))
      case Contains =>
        (s, c.copy(fromIndex = c.fromIndex - 1, toIndex = c.toIndex - 1))
      case ContainedBy =>
        (s.copy(fromIndex = s.fromIndex + 1, toIndex = s.toIndex + 1), c)
      case Finishes =>
        (s.copy(fromIndex = s.fromIndex + 1), c.copy(fromIndex = c.fromIndex - 1))
      case FinishedBy =>
        (s, c.copy(fromIndex = c.fromIndex - 1, toIndex = c.toIndex - 1))
      case EqualTo =>
        (s.copy(fromIndex = s.fromIndex + 1), c.copy(fromIndex = c.fromIndex - 1))
    }
  }
}
