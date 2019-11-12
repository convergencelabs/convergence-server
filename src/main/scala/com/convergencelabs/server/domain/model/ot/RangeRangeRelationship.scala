/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

package com.convergencelabs.server.domain.model.ot

object RangeRangeRelationship extends Enumeration {
  val Precedes, PrecededBy, Meets, MetBy, Overlaps, OverlappedBy, Starts, StartedBy, Contains, ContainedBy, Finishes, FinishedBy, EqualTo = Value
}

object RangeIndexRelationship extends Enumeration {
  val Before, Start, Within, End, After = Value
}
