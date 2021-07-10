/*
 * Copyright (c) 2021 - Convergence Labs, Inc.
 *
 * This file is part of the Convergence Server, which is released under
 * the terms of the GNU General Public License version 3 (GPLv3). A copy
 * of the GPLv3 should have been provided along with this file, typically
 * located in the "LICENSE" file, which is part of this source code package.
 * Alternatively, see <https://www.gnu.org/licenses/gpl-3.0.html> for the
 * full text of the GPLv3 license, if it was not provided.
 */

package com.convergencelabs.convergence.server.backend.services.domain.model.ot.xform.string

import com.convergencelabs.convergence.server.backend.services.domain.model.ot.RangeRangeRelationship.{ContainedBy, Contains, EqualTo, FinishedBy, Finishes, Meets, MetBy, OverlappedBy, Overlaps, PrecededBy, Precedes, StartedBy, Starts}
import com.convergencelabs.convergence.server.backend.services.domain.model.ot._
import com.convergencelabs.convergence.server.backend.services.domain.model.ot.xform.OperationTransformationFunction



private[ot] object StringSpliceSpliceTF extends OperationTransformationFunction[StringSpliceOperation, StringSpliceOperation] {
  private object SpliceType extends Enumeration {
    val Insert, Remove, Splice, NoOp = Value
  }

  def transform(s: StringSpliceOperation, c: StringSpliceOperation): (StringSpliceOperation, StringSpliceOperation) = {
    val sType = getSpliceType(s)
    val cType = getSpliceType(c)

    (sType, cType) match {
      case (SpliceType.NoOp, _) | (_, SpliceType.NoOp) =>
        (s, c)

      case (SpliceType.Insert, SpliceType.Insert) =>
        transformInsertInsert(s, c)
      case (SpliceType.Insert, SpliceType.Remove) =>
        transformInsertRemove(s, c)
      case (SpliceType.Insert, SpliceType.Splice) =>
        transformInsertSplice(s, c)

      case (SpliceType.Remove, SpliceType.Insert) =>
        transformRemoveInsert(s, c)
      case (SpliceType.Remove, SpliceType.Remove) =>
        transformRemoveRemove(s, c)
      case (SpliceType.Remove, SpliceType.Splice) =>
        transformRemoveSplice(s, c)

      case (SpliceType.Splice, SpliceType.Insert) =>
        transformSpliceInsert(s, c)
      case (SpliceType.Splice, SpliceType.Remove) =>
        transformSpliceRemove(s, c)
      case (SpliceType.Splice, SpliceType.Splice) =>
        transformSpliceSplice(s, c)
    }
  }

  private def transformInsertInsert(s: StringSpliceOperation, c: StringSpliceOperation): (StringSpliceOperation, StringSpliceOperation) = {
    if (s.index <= c.index) {
      // S-II-1 and S-II-2
      (s, c.copy(index = c.index + s.insertValue.length))
    } else {
      // S-II-3
      (s.copy(index = s.index + c.insertValue.length), c)
    }
  }

  private def transformInsertRemove(s: StringSpliceOperation, c: StringSpliceOperation): (StringSpliceOperation, StringSpliceOperation) = {
    if (s.index <= c.index) {
      // S-IR-1 and S-IR-2
      (s, c.copy(index = c.index + s.insertValue.length))
    } else if (s.index >= c.index + c.deleteCount) {
      // S-IR-5
      (s.copy(index = s.index - c.deleteCount), c)
    } else {
      // S-IR-3 and S-IR-4
      (s.copy(noOp = true), c.copy(deleteCount = c.deleteCount + s.insertValue.length))
    }
  }

  private def transformInsertSplice(s: StringSpliceOperation, c: StringSpliceOperation): (StringSpliceOperation, StringSpliceOperation) = {
    if (s.index <= c.index) {
      (
        s,
        c.copy(index = c.index + s.insertValue.length),
      )
    } else if (c.index + c.deleteCount > s.index) {
      (
        s.copy(noOp = true),
        c.copy(deleteCount = c.deleteCount + s.insertValue.length),
      )
    } else {
      (
        s.copy(index = s.index - c.deleteCount + c.insertValue.length),
        c
      )
    }
  }

  private def transformRemoveInsert(s: StringSpliceOperation, c: StringSpliceOperation): (StringSpliceOperation, StringSpliceOperation) = {
    if (c.index <= s.index) {
      // S-RI-1 and S-RI-2
      (s.copy(index = s.index + c.insertValue.length), c)
    } else if (c.index >= s.index + s.deleteCount) {
      // S-RI-5
      (s, c.copy(index = c.index - s.deleteCount))
    } else {
      // S-RI-3 and S-RI-4
      (s.copy(deleteCount = s.deleteCount + c.insertValue.length), c.copy(noOp = true))
    }
  }

  private def transformRemoveRemove(s: StringSpliceOperation, c: StringSpliceOperation): (StringSpliceOperation, StringSpliceOperation) = {
    val cStart = c.index
    val cEnd = c.index + c.deleteCount

    val sStart = s.index
    val sEnd = s.index + s.deleteCount

    RangeRelationshipUtil.getRangeRangeRelationship(sStart, sEnd, cStart, cEnd) match {
      case Precedes =>
        // S-RR-1
        (
          s,
          c.copy(index = c.index - s.deleteCount)
        )

      case PrecededBy =>
        // S-RR-2
        (
          s.copy(index = s.index - c.deleteCount),
          c
        )

      case Meets | Overlaps =>
        // S-RR-3
        // S-RR-5
        val overlapLength = s.index + s.deleteCount - c.index
        (
          s.copy(deleteCount = s.deleteCount - overlapLength),
          c.copy(index = s.index, deleteCount = c.deleteCount - overlapLength)
        )

      case MetBy | OverlappedBy =>
        // S-RR-4
        // S-RR-6
        val overlapLength = c.index + c.deleteCount - s.index
        (
          s.copy(index = c.index, deleteCount = s.deleteCount - overlapLength),
          c.copy(deleteCount = c.deleteCount - overlapLength)
        )

      case Starts | ContainedBy | Finishes =>
        // S-RR-7
        // S-RR-10
        // S-RR-11
        (
          s.copy(noOp = true),
          c.copy(deleteCount = c.deleteCount - s.deleteCount)
        )

      case StartedBy | Contains | FinishedBy =>
        // S-RR-8
        // S-RR-9
        // S-RR-12
        (
          s.copy(deleteCount = s.deleteCount - c.deleteCount),
          c.copy(noOp = true)
        )

      case EqualTo =>
        // S-RR-13
        (
          s.copy(noOp = true),
          c.copy(noOp = true)
        )
    }
  }

  private def transformRemoveSplice(s: StringSpliceOperation, c: StringSpliceOperation): (StringSpliceOperation, StringSpliceOperation) = {
    val cStart = c.index
    val cEnd = c.index + c.deleteCount

    val sStart = s.index
    val sEnd = s.index + s.deleteCount

    RangeRelationshipUtil.getRangeRangeRelationship(sStart, sEnd, cStart, cEnd) match {
      case Precedes =>
        (
          s,
          c.copy(index = c.index - s.deleteCount)
        )

      case PrecededBy =>
        (
          s.copy(index = s.index - c.deleteCount + c.insertValue.length),
          c
        )

      case Meets | Overlaps =>
        val overlapLength = s.index + s.deleteCount - c.index
        (
          s.copy(deleteCount = s.deleteCount - overlapLength),
          c.copy(index = s.index, deleteCount = c.deleteCount - overlapLength)
        )

      case MetBy | OverlappedBy =>
        val overlapLength = c.index + c.deleteCount - s.index
        (
          s.copy(index = c.index + c.insertValue.length, deleteCount = s.deleteCount - overlapLength),
          c.copy(deleteCount = c.deleteCount - overlapLength)
        )

      case Starts | Finishes | ContainedBy =>
        (
          s.copy(noOp = true),
          c.copy(deleteCount = c.deleteCount - s.deleteCount)
        )

      case StartedBy =>
        (
          s.copy(index = s.index + c.insertValue.length, deleteCount = s.deleteCount - c.deleteCount),
          c.copy(index = s.index, deleteCount = 0)
        )

      case FinishedBy =>
        (
          s.copy(deleteCount = s.deleteCount - c.deleteCount),
          c.copy(index = s.index, deleteCount = 0)
        )

      case Contains =>
        (
          s.copy(deleteCount = s.deleteCount - c.deleteCount + c.insertValue.length),
          c.copy(noOp = true)
        )

      case EqualTo =>
        (
          s.copy(noOp = true),
          c.copy(deleteCount = 0)
        )
    }
  }

  private def transformSpliceInsert(s: StringSpliceOperation, c: StringSpliceOperation): (StringSpliceOperation, StringSpliceOperation) = {
    if (c.index <= s.index) {
      (
        s.copy(index = s.index + c.insertValue.length),
        c
      )
    } else if (s.index + s.deleteCount > c.index) {
      (
        s.copy(deleteCount = s.deleteCount + c.insertValue.length),
        c.copy(noOp = true)
      )
    } else {
      (
        s,
        c.copy(index = c.index - s.deleteCount + s.insertValue.length)
      )
    }
  }

  private def transformSpliceRemove(s: StringSpliceOperation, c: StringSpliceOperation): (StringSpliceOperation, StringSpliceOperation) = {
    val cStart = c.index
    val cEnd = c.index + c.deleteCount

    val sStart = s.index
    val sEnd = s.index + s.deleteCount

    RangeRelationshipUtil.getRangeRangeRelationship(sStart, sEnd, cStart, cEnd) match {
      case Precedes =>
        (
          s,
          c.copy(index = c.index - s.deleteCount + s.insertValue.length)
        )

      case PrecededBy =>
        (
          s.copy(index = s.index - c.deleteCount),
          c
        )

      case Meets | Overlaps =>
        val overlapLength = s.index + s.deleteCount - c.index
        (
          s.copy(deleteCount = s.deleteCount - overlapLength),
          c.copy(index = s.index + s.insertValue.length, deleteCount = c.deleteCount - overlapLength)
        )

      case MetBy | OverlappedBy =>
        val overlapLength = c.index + c.deleteCount - s.index
        (
          s.copy(index = c.index, deleteCount = s.deleteCount - overlapLength),
          c.copy(deleteCount = c.deleteCount - overlapLength)
        )

      case Starts =>
        (
          s.copy(deleteCount = 0),
          c.copy(index = c.index + s.insertValue.length, deleteCount = c.deleteCount - s.deleteCount)
        )
      case Finishes =>
        (
          s.copy(index = c.index, deleteCount = 0),
          c.copy(deleteCount = c.deleteCount - s.deleteCount)
        )

      case ContainedBy =>
        (
          s.copy(noOp = true),
          c.copy(deleteCount = c.deleteCount - s.deleteCount + s.insertValue.length)
        )

      case StartedBy | FinishedBy | Contains =>
        (
          s.copy(deleteCount = s.deleteCount - c.deleteCount),
          c.copy(deleteCount = 0)
        )

      case EqualTo =>
        (
          s.copy(deleteCount = 0),
          c.copy(noOp = true)
        )
    }
  }

  private def transformSpliceSplice(s: StringSpliceOperation, c: StringSpliceOperation): (StringSpliceOperation, StringSpliceOperation) = {
    val cStart = c.index
    val cEnd = c.index + c.deleteCount

    val sStart = s.index
    val sEnd = s.index + s.deleteCount

    RangeRelationshipUtil.getRangeRangeRelationship(sStart, sEnd, cStart, cEnd) match {
      case Precedes =>
        (
          s,
          c.copy(index = c.index - s.deleteCount + s.insertValue.length)
        )

      case PrecededBy =>
        // S-RR-2
        (
          s.copy(index = s.index - c.deleteCount + s.insertValue.length),
          c
        )

      case Meets | Overlaps =>
        val overlapLength = s.index + s.deleteCount - c.index
        (
          s.copy(deleteCount = s.deleteCount - overlapLength),
          c.copy(index = s.index + s.insertValue.length, deleteCount = c.deleteCount - overlapLength)
        )

      case MetBy | OverlappedBy =>
        val overlapLength = c.index + c.deleteCount - s.index
        (
          s.copy(index = c.index + c.insertValue.length, deleteCount = s.deleteCount - overlapLength),
          c.copy(deleteCount = c.deleteCount - overlapLength)
        )

      case Starts =>
        (
          s.copy(deleteCount = 0),
          c.copy(index = c.index + s.insertValue.length, deleteCount = c.deleteCount - s.deleteCount)
        )

      case ContainedBy =>
        (
          s.copy(noOp = true),
          c.copy(deleteCount = c.deleteCount - s.deleteCount + s.insertValue.length)
        )

      case Finishes =>
        (
          s.copy(index = c.index + c.insertValue.length, deleteCount = 0),
          c.copy(deleteCount = c.deleteCount - s.deleteCount)
        )

      case StartedBy =>
        (
          s.copy(index = s.index + c.insertValue.length, deleteCount = s.deleteCount - c.deleteCount),
          c.copy(deleteCount = 0)
        )

      case Contains =>
        (
          s.copy(deleteCount = s.deleteCount - c.deleteCount + c.insertValue.length),
          c.copy(noOp = true)
        )

      case FinishedBy =>
        (
          s.copy(deleteCount = s.deleteCount - c.deleteCount),
          c.copy(deleteCount = 0, index = s.index + s.insertValue.length),
        )

      case EqualTo =>
        (
          s.copy(deleteCount = 0),
          c.copy(index = c.index + s.insertValue.length, deleteCount = 0),
        )
    }
  }

  private def getSpliceType(op: StringSpliceOperation): SpliceType.Value = {
    if (op.deleteCount == 0 && op.insertValue.nonEmpty) {
      SpliceType.Insert
    } else if (op.deleteCount > 0 && op.insertValue.isEmpty) {
      SpliceType.Remove
    } else if (op.deleteCount > 0 && op.insertValue.nonEmpty) {
      SpliceType.Splice
    } else {
      SpliceType.NoOp
    }
  }
}
