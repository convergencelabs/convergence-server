package com.convergencelabs.server.frontend.realtime.model

object OperationType extends Enumeration {
  val Compound = 0
  val ArrayInsert = 1
  val ArrayReorder = 2
  val ArrayRemove = 3
  val ArraySet = 4
  val ArrayValue = 5
  val BooleanValue = 6
  val NumberAdd = 7
  val NumberValue = 8
  val ObjectAdd = 9
  val ObjectRemove = 10
  val ObjectSet = 11
  val ObjectValue = 12
  val StringInsert = 13
  val StringRemove = 14
  val StringValue = 15
  val DateValue = 16
}
