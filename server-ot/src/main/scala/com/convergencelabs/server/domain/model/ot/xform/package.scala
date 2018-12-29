package com.convergencelabs.server.domain

package model {
  object ReferenceType extends Enumeration {
    val Index, Range, Property, Element = Value
  }

  case class ReferenceValue(id: Option[String], key: String, referenceType: ReferenceType.Value, values: List[Any], contextVersion: Long)
}
