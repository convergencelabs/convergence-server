package com.convergencelabs.server.domain.model.ot

import RangeRangeRelationship._

private[ot] object StringRemoveRemoveTF extends OperationTransformationFunction[StringRemoveOperation, StringRemoveOperation] {
  def transform(s: StringRemoveOperation, c: StringRemoveOperation): (StringOperation, StringOperation) = {
    val cStart = c.index
    val cEnd = c.index + c.value.length()

    val sStart = s.index
    val sEnd = s.index + s.value.length()

    RangeRelationshipUtil.getRangeRangeRelationship(sStart, sEnd, cStart, cEnd) match {
      case EqualTo =>
        (s.copy(noOp = true), c.copy(noOp = true))
      case Precedes =>
        (s, c.copy(index = c.index - s.value.length))
      case PrecededBy =>
        (s.copy(index = s.index - c.value.length), c)
      case Meets | Overlaps =>
        val offsetDelta = c.index - s.index
        (s.copy(value = s.value.substring(0, offsetDelta)),
          c.copy(index = s.index, value = c.value.substring(s.value.length - offsetDelta, c.value.length)))
      case MetBy | OverlappedBy =>
        val offsetDelta = s.index - c.index
        (s.copy(index = c.index, value = s.value.substring(c.value.length() - offsetDelta, s.value.length)),
          c.copy(value = c.value.substring(0, offsetDelta)))
      case Contains =>
        val overlapStart = c.index - s.index
        val overlapEnd = overlapStart + c.value.length
        (s.copy(value = s.value.substring(0, overlapStart) + s.value.substring(overlapEnd, s.value.length)), c.copy(noOp = true))
      case ContainedBy =>
        val overlapStart = s.index - c.index
        val overlapEnd = overlapStart + s.value.length()
        (s.copy(noOp = true), c.copy(value = c.value.substring(0, overlapStart) + c.value.substring(overlapEnd, c.value.length)))
      case Starts =>
        (s.copy(noOp = true), c.copy(value = c.value.substring(s.value.length, c.value.length)))
      case StartedBy =>
        (s.copy(value = s.value.substring(c.value.length, s.value.length)), c.copy(noOp = true))
      case Finishes =>
        (s.copy(noOp = true), c.copy(value = c.value.substring(0, c.value.length - s.value.length)))
      case FinishedBy =>
        (s.copy(value = s.value.substring(0, s.value.length - c.value.length)), c.copy(noOp = true))
    }
  }
}
