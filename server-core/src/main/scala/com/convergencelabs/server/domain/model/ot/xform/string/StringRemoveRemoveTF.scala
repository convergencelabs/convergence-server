package com.convergencelabs.server.domain.model.ot

import RangeRangeRelationship.ContainedBy
import RangeRangeRelationship.Contains
import RangeRangeRelationship.EqualTo
import RangeRangeRelationship.FinishedBy
import RangeRangeRelationship.Finishes
import RangeRangeRelationship.Meets
import RangeRangeRelationship.MetBy
import RangeRangeRelationship.OverlappedBy
import RangeRangeRelationship.Overlaps
import RangeRangeRelationship.PrecededBy
import RangeRangeRelationship.Precedes
import RangeRangeRelationship.StartedBy
import RangeRangeRelationship.Starts

/**
 * This transformation function handles a concurrent server
 * StringRemoveOperation and a client StringRemoveOperation.
 */
private[ot] object StringRemoveRemoveTF extends OperationTransformationFunction[StringRemoveOperation, StringRemoveOperation] {
  // scalastyle:off cyclomatic.complexity
  def transform(s: StringRemoveOperation, c: StringRemoveOperation): (StringOperation, StringOperation) = {
    val cStart = c.index
    val cEnd = c.index + c.value.length()

    val sStart = s.index
    val sEnd = s.index + s.value.length()

    RangeRelationshipUtil.getRangeRangeRelationship(sStart, sEnd, cStart, cEnd) match {
      case Precedes =>
        // S-RR-1
        (s, c.copy(index = c.index - s.value.length))
      case PrecededBy =>
        // S-RR-2
        (s.copy(index = s.index - c.value.length), c)
      case Meets | Overlaps =>
        // S-RR-3 and S-RR-5
        val offsetDelta = c.index - s.index
        (s.copy(value = s.value.substring(0, offsetDelta)),
          c.copy(index = s.index, value = c.value.substring(s.value.length - offsetDelta, c.value.length)))
      case MetBy | OverlappedBy =>
        // S-RR-4 and S-RR-6
        val offsetDelta = s.index - c.index
        (s.copy(index = c.index, value = s.value.substring(c.value.length() - offsetDelta, s.value.length)),
          c.copy(value = c.value.substring(0, offsetDelta)))
      case Starts =>
        // S-RR-7
        (s.copy(noOp = true), c.copy(value = c.value.substring(s.value.length, c.value.length)))
      case StartedBy =>
        // S-RR-8
        (s.copy(value = s.value.substring(c.value.length, s.value.length)), c.copy(noOp = true))
      case Contains =>
        // S-RR-9
        val overlapStart = c.index - s.index
        val overlapEnd = overlapStart + c.value.length
        (s.copy(value = s.value.substring(0, overlapStart) + s.value.substring(overlapEnd, s.value.length)), c.copy(noOp = true))
      case ContainedBy =>
        // S-RR-10
        val overlapStart = s.index - c.index
        val overlapEnd = overlapStart + s.value.length()
        (s.copy(noOp = true), c.copy(value = c.value.substring(0, overlapStart) + c.value.substring(overlapEnd, c.value.length)))
      case Finishes =>
        // S-RR-11
        (s.copy(noOp = true), c.copy(value = c.value.substring(0, c.value.length - s.value.length)))
      case FinishedBy =>
        // S-RR-12
        (s.copy(value = s.value.substring(0, s.value.length - c.value.length)), c.copy(noOp = true))
      case EqualTo =>
        // S-RR-13
        (s.copy(noOp = true), c.copy(noOp = true))
    }
  }
}
