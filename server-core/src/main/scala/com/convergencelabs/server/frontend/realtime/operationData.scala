package com.convergencelabs.server.frontend.realtime

import org.json4s.JsonAST.JValue
import org.json4s.JsonAST.JArray
import org.json4s.JsonAST.JObject
import com.convergencelabs.server.domain.model.data.DataValue

sealed trait OperationData

case class CompoundOperationData(o: List[DiscreteOperationData]) extends OperationData

sealed trait DiscreteOperationData extends OperationData {
  def d: String
  def n: Boolean
}

sealed trait StringOperaitonData extends DiscreteOperationData
case class StringInsertOperationData(d: String, n: Boolean, i: Int, v: String) extends StringOperaitonData
case class StringRemoveOperationData(d: String, n: Boolean, i: Int, v: String) extends StringOperaitonData
case class StringSetOperationData(d: String, n: Boolean, v: String) extends StringOperaitonData

sealed trait ArrayOperaitonData extends DiscreteOperationData
case class ArrayInsertOperationData(d: String, n: Boolean, i: Int, v: DataValue) extends ArrayOperaitonData
case class ArrayRemoveOperationData(d: String, n: Boolean, i: Int) extends ArrayOperaitonData
case class ArrayReplaceOperationData(d: String, n: Boolean, i: Int, v: DataValue) extends ArrayOperaitonData
case class ArrayMoveOperationData(d: String, n: Boolean, f: Int, o: Int) extends ArrayOperaitonData
case class ArraySetOperationData(d: String, n: Boolean, v: List[DataValue]) extends ArrayOperaitonData

sealed trait ObjectOperaitonData extends DiscreteOperationData
case class ObjectAddPropertyOperationData(d: String, n: Boolean, k: String, v: DataValue) extends ObjectOperaitonData
case class ObjectSetPropertyOperationData(d: String, n: Boolean, k: String, v: DataValue) extends ObjectOperaitonData
case class ObjectRemovePropertyOperationData(d: String, n: Boolean, k: String) extends ObjectOperaitonData
case class ObjectSetOperationData(d: String, n: Boolean, v: Map[String, DataValue]) extends ObjectOperaitonData

sealed trait NumberOperaitonData extends DiscreteOperationData
case class NumberAddOperationData(d: String, n: Boolean, v: Double) extends NumberOperaitonData
case class NumberSetOperationData(d: String, n: Boolean, v: Double) extends NumberOperaitonData

sealed trait BooleanOperaitonData extends DiscreteOperationData
case class BooleanSetOperationData(d: String, n: Boolean, v: Boolean) extends BooleanOperaitonData
