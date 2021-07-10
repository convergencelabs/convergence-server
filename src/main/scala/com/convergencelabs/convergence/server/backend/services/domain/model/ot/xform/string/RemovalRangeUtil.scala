package com.convergencelabs.convergence.server.backend.services.domain.model.ot.xform.string

import com.convergencelabs.convergence.server.backend.services.domain.model.ot.RangeRelationshipUtil
import com.convergencelabs.convergence.server.backend.services.domain.model.ot.RangeRangeRelationship._

private[ot] object RemovalRangeUtil {

  final case class RemoveRange(index: Int, length: Int) {
    def isEmpty: Boolean = length == 0

    def nonEmpty: Boolean = length != 0
  }

  def transform(serverRange: RemoveRange, clientRange: RemoveRange): (RemoveRange, RemoveRange) = {
    val sStart = serverRange.index
    val sEnd = serverRange.index + serverRange.length

    val cStart = clientRange.index
    val cEnd = clientRange.index + clientRange.length

    RangeRelationshipUtil.getRangeRangeRelationship(sStart, sEnd, cStart, cEnd) match {
      case Precedes =>
        // S-RR-1
        (
          serverRange,
          clientRange.copy(index = clientRange.index - serverRange.length)
        )

      case PrecededBy =>
        // S-RR-2
        (
          serverRange.copy(index = serverRange.index - clientRange.length),
          clientRange
        )

      case Meets | Overlaps =>
        // S-RR-3
        // S-RR-5
        val overlapLength = serverRange.index + serverRange.length - clientRange.index
        (
          serverRange.copy(length = serverRange.length - overlapLength),
          clientRange.copy(index = serverRange.index, clientRange.length - overlapLength)
        )

      case MetBy | OverlappedBy =>
        // S-RR-4
        // S-RR-6
        val overlapLength = clientRange.index + clientRange.length - serverRange.index
        (
          serverRange.copy(index = clientRange.index, serverRange.length - overlapLength),
          clientRange.copy(length = clientRange.length - overlapLength)
        )

      case Starts | ContainedBy | Finishes =>
        // S-RR-7
        // S-RR-10
        // S-RR-11
        (
          serverRange.copy(length = 0),
          clientRange.copy(length = clientRange.length - serverRange.length)
        )

      case StartedBy | Contains | FinishedBy =>
        // S-RR-8
        // S-RR-9
        // S-RR-12
        (
          serverRange.copy(length = serverRange.length - clientRange.length),
          clientRange.copy(length = 0)
        )

      case EqualTo =>
        // S-RR-13
        (
          serverRange.copy(length = 0),
          clientRange.copy(length = 0)
        )
    }
  }
}
