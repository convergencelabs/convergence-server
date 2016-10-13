package com.convergencelabs.server.domain

package model {
  object ReferenceType extends Enumeration {
    val Index = Value(0)
    val Range = Value(1)
    val Property = Value(2)
    val Element = Value(3)

    // FIXME do we need to do this at all?
    def map(code: Int): ReferenceType.Value = {
      code match {
        case 0 => Index
        case 1 => Range
        case 2 => Property
        case 3 => Element
      }
    }

    def map(value: ReferenceType.Value): Int = {
      value match {
        case Index    => 0
        case Range    => 1
        case Property => 2
        case Element  => 3
      }
    }
  }

  case class ReferenceValue(id: Option[String], key: String, referenceType: ReferenceType.Value, values: List[Any], contextVersion: Long)
}
