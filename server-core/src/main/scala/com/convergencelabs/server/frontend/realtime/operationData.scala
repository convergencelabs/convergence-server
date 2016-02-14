package com.convergencelabs.server.frontend.realtime

import org.json4s.JsonAST.JValue
import org.json4s.JsonAST.JArray
import org.json4s.JsonAST.JObject

sealed trait OperationData

case class CompoundOperationData(o: List[DiscreteOperationData]) extends OperationData

sealed trait DiscreteOperationData extends OperationData {
  def p: List[Any]
  def n: Boolean
}

sealed trait StringOperaitonData extends DiscreteOperationData
case class StringInsertOperationData(p: List[Any], n: Boolean, i: Int, v: String) extends StringOperaitonData
case class StringRemoveOperationData(p: List[Any], n: Boolean, i: Int, v: String) extends StringOperaitonData
case class StringSetOperationData(p: List[Any], n: Boolean, v: String) extends StringOperaitonData

sealed trait ArrayOperaitonData extends DiscreteOperationData
case class ArrayInsertOperationData(p: List[Any], n: Boolean, i: Int, v: JValue) extends ArrayOperaitonData
case class ArrayRemoveOperationData(p: List[Any], n: Boolean, i: Int) extends ArrayOperaitonData
case class ArrayReplaceOperationData(p: List[Any], n: Boolean, i: Int, v: JValue) extends ArrayOperaitonData
case class ArrayMoveOperationData(p: List[Any], n: Boolean, f: Int, o: Int) extends ArrayOperaitonData
case class ArraySetOperationData(p: List[Any], n: Boolean, v: JArray) extends ArrayOperaitonData

sealed trait ObjectOperaitonData extends DiscreteOperationData
case class ObjectAddPropertyOperationData(p: List[Any], n: Boolean, k: String, v: JValue) extends ObjectOperaitonData
case class ObjectSetPropertyOperationData(p: List[Any], n: Boolean, k: String, v: JValue) extends ObjectOperaitonData
case class ObjectRemovePropertyOperationData(p: List[Any], n: Boolean, k: String) extends ObjectOperaitonData
case class ObjectSetOperationData(p: List[Any], n: Boolean, v: JObject) extends ObjectOperaitonData

sealed trait NumberOperaitonData extends DiscreteOperationData
case class NumberAddOperationData(p: List[Any], n: Boolean, v: JValue) extends NumberOperaitonData
case class NumberSetOperationData(p: List[Any], n: Boolean, v: JValue) extends NumberOperaitonData

sealed trait BooleanOperaitonData extends DiscreteOperationData
case class BooleanSetOperationData(p: List[Any], n: Boolean, v: Boolean) extends BooleanOperaitonData
