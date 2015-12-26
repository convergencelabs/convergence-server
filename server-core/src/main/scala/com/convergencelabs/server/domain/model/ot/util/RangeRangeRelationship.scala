package com.convergencelabs.server.domain.model.ot

object RangeRangeRelationship extends Enumeration {
  val Precedes, 
  PrecededBy, 
  Meets, 
  MetBy, 
  Overlaps, 
  OverlappedBy, 
  Starts, 
  StartedBy, 
  Contains, 
  ContainedBy, 
  Finishes, 
  FinishedBy, 
  EqualTo = Value
}

object RangeIndexRelationship extends Enumeration {
  val Before,
  Start,
  Within,
  End,
  After = Value
}