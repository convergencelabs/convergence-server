package com.convergencelabs.server.domain.model.data

sealed trait DataValue {
  val id: String
}

case class ObjectValue(id: String, children: Map[String, DataValue]) extends DataValue

case class ArrayValue(id: String, children: List[DataValue]) extends DataValue

case class BooleanValue(id: String, value: Boolean) extends DataValue

case class DoubleValue(id: String, value: Double) extends DataValue {
  def this(id: String, value: Int) = this(id, value.doubleValue())
}

case class NullValue(id: String) extends DataValue

case class StringValue(id: String, value: String) extends DataValue
