package com.convergencelabs.server.domain.model

object ReferenceType extends Enumeration {
  val Index = Value(0)
  val Range = Value(1)

  // FIXME do we need to do this at all?
  def map(code: Int): ReferenceType.Value = {
    code match {
      case 0 => Index
      case 1 => Range
    }
  }

  def map(value: ReferenceType.Value): Int = {
    value match {
      case Index => 0
      case Range => 1
    }
  }
}
