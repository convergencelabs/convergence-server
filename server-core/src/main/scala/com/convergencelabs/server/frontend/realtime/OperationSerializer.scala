package com.convergencelabs.server.frontend.realtime

import org.json4s.DefaultFormats
import org.json4s.Extraction
import org.json4s.JsonAST.JValue
import org.json4s.jackson.Serialization.read
import org.json4s.jackson.Serialization.write
import org.json4s.reflect.Reflector
import com.convergencelabs.server.util.BiMap
import com.convergencelabs.server.frontend.realtime.model.OperationType
import com.convergencelabs.server.frontend.realtime.data._
import org.json4s.CustomSerializer
import com.convergencelabs.server.domain.model.ot._
import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._
import com.convergencelabs.server.domain.model.data.DataValue

object utils {
  def jnumberToDouble(value: JValue): Double = {
    value match {
      case JInt(x) => x.doubleValue()
      case JDouble(x) => x
      case JLong(x) => x.doubleValue()
      case _ => ???
    }
  }
}

object Big {
  def unapply(n: BigInt) = Some(n.toInt)
}

import utils._

class OperationSerializer extends CustomSerializer[OperationData](format => ({
  case JObject(List(("t", JInt(Big(OperationType.Compound))), ("o", JArray(ops)))) =>
    implicit val f = format;
    CompoundOperationData(ops.map { op => Extraction.extract[OperationData](op).asInstanceOf[DiscreteOperationData] })

  case JObject(List(("t", JInt(Big(OperationType.ArrayInsert))), ("d", JString(id)), ("n", JBool(noOp)), ("i", JInt(Big(index))), ("v", value))) =>
    implicit val f = format;
    ArrayInsertOperationData(id, noOp, index, Extraction.extract[DataValue](value))
  case JObject(List(("t", JInt(Big(OperationType.ArrayRemove))), ("d", JString(id)), ("n", JBool(noOp)), ("i", JInt(Big(index))))) =>
    ArrayRemoveOperationData(id, noOp, index)
  case JObject(List(("t", JInt(Big(OperationType.ArraySet))), ("d", JString(id)), ("n", JBool(noOp)), ("i", JInt(Big(index))), ("v", value))) =>
    implicit val f = format;
    ArrayReplaceOperationData(id, noOp, index, Extraction.extract[DataValue](value))
  case JObject(List(("t", JInt(Big(OperationType.ArrayReorder))), ("d", JString(id)), ("n", JBool(noOp)), ("f", JInt(Big(from))), ("o", JInt(Big(to))))) =>
    ArrayMoveOperationData(id, noOp, from, to)
  case JObject(List(("t", JInt(Big(OperationType.ArrayValue))), ("d", JString(id)), ("n", JBool(noOp)), ("v", JArray(values)))) =>
    implicit val f = format;
    ArraySetOperationData(id, noOp, values.map { value => Extraction.extract[DataValue](value) })

  case JObject(List(("t", JInt(Big(OperationType.ObjectAdd))), ("d", JString(id)), ("n", JBool(noOp)), ("k", JString(prop)), ("v", value))) =>
    implicit val f = format;
    ObjectAddPropertyOperationData(id, noOp, prop, Extraction.extract[DataValue](value))
  case JObject(List(("t", JInt(Big(OperationType.ObjectSet))), ("d", JString(id)), ("n", JBool(noOp)), ("k", JString(prop)), ("v", value))) =>
    implicit val f = format;
    ObjectSetPropertyOperationData(id, noOp, prop, Extraction.extract[DataValue](value))
  case JObject(List(("t", JInt(Big(OperationType.ObjectRemove))), ("d", JString(id)), ("n", JBool(noOp)), ("k", JString(prop)))) =>
    ObjectRemovePropertyOperationData(id, noOp, prop)
  case JObject(List(("t", JInt(Big(OperationType.ObjectValue))), ("d", JString(id)), ("n", JBool(noOp)), ("v", JObject(fields)))) =>
    implicit val f = format;
    ObjectSetOperationData(id, noOp, fields.toMap.map { case (k, v) => (k, Extraction.extract[DataValue](v)) })

  case JObject(List(("t", JInt(Big(OperationType.StringInsert))), ("d", JString(id)), ("n", JBool(noOp)), ("i", JInt(Big(index))), ("v", JString(value)))) =>
    StringInsertOperationData(id, noOp, index, value)
  case JObject(List(("t", JInt(Big(OperationType.StringRemove))), ("d", JString(id)), ("n", JBool(noOp)), ("i", JInt(Big(index))), ("v", JString(value)))) =>
    StringRemoveOperationData(id, noOp, index, value)
  case JObject(List(("t", JInt(Big(OperationType.StringValue))), ("d", JString(id)), ("n", JBool(noOp)), ("v", JString(value)))) =>
    StringSetOperationData(id, noOp, value)

  case JObject(List(("t", JInt(Big(OperationType.NumberAdd))), ("d", JString(id)), ("n", JBool(noOp)), ("v", value))) =>
    NumberAddOperationData(id, noOp, jnumberToDouble(value))
  case JObject(List(("t", JInt(Big(OperationType.NumberValue))), ("d", JString(id)), ("n", JBool(noOp)), ("v", value))) =>
    NumberSetOperationData(id, noOp, jnumberToDouble(value))

  case JObject(List(("t", JInt(Big(OperationType.BooleanValue))), ("d", JString(id)), ("n", JBool(noOp)), ("v", JBool(value)))) =>
    BooleanSetOperationData(id, noOp, value)
}, {
  case CompoundOperationData(ops) =>
    ("t" -> OperationType.Compound) ~
      ("o" -> ops.map { op =>
        Extraction.decompose(op)(format).asInstanceOf[JObject]
      })

  case ArrayInsertOperationData(id, noOp, index, value) =>
    ("t" -> OperationType.ArrayInsert) ~ ("d" -> id) ~ ("n" -> noOp) ~ ("i" -> index) ~
      ("v" -> Extraction.decompose(value)(format).asInstanceOf[JObject])
  case ArrayRemoveOperationData(id, noOp, index) =>
    ("t" -> OperationType.ArrayRemove) ~ ("d" -> id) ~ ("n" -> noOp) ~ ("i" -> index)
  case ArrayReplaceOperationData(id, noOp, index, value) =>
    ("t" -> OperationType.ArraySet) ~ ("d" -> id) ~ ("n" -> noOp) ~ ("i" -> index) ~
      ("v" -> Extraction.decompose(value)(format).asInstanceOf[JObject])
  case ArrayMoveOperationData(id, noOp, from, to) =>
    ("t" -> OperationType.ArrayReorder) ~ ("d" -> id) ~ ("n" -> noOp) ~ ("f" -> from) ~ ("o" -> to)
  case ArraySetOperationData(id, noOp, value) =>
    ("t" -> OperationType.ArrayValue) ~ ("d" -> id) ~ ("n" -> noOp) ~
      ("v" -> Extraction.decompose(value)(format).asInstanceOf[JObject])

  case ObjectSetPropertyOperationData(id, noOp, prop, value) =>
    ("t" -> OperationType.ObjectSet) ~ ("d" -> id) ~ ("n" -> noOp) ~ ("p" -> prop)
    ("v" -> Extraction.decompose(value)(format).asInstanceOf[JObject])
  case ObjectAddPropertyOperationData(id, noOp, prop, value) =>
    ("t" -> OperationType.ObjectAdd) ~ ("d" -> id) ~ ("n" -> noOp) ~ ("p" -> prop)
    ("v" -> Extraction.decompose(value)(format).asInstanceOf[JObject])
  case ObjectRemovePropertyOperationData(id, noOp, prop) =>
    ("t" -> OperationType.ObjectAdd) ~ ("d" -> id) ~ ("n" -> noOp) ~ ("p" -> prop)
  case ObjectSetOperationData(id, noOp, value) =>
    ("t" -> OperationType.ObjectValue) ~ ("d" -> id) ~ ("n" -> noOp) ~
      ("v" -> Extraction.decompose(value)(format).asInstanceOf[JObject])

  case StringInsertOperationData(id, noOp, index, value) =>
    ("t" -> OperationType.StringInsert) ~ ("d" -> id) ~ ("n" -> noOp) ~ ("i" -> index) ~ ("v" -> value)
  case StringRemoveOperationData(id, noOp, index, value) =>
    ("t" -> OperationType.StringRemove) ~ ("d" -> id) ~ ("n" -> noOp) ~ ("i" -> index) ~ ("v" -> value)
  case StringSetOperationData(id, noOp, value) =>
    ("t" -> OperationType.StringValue) ~ ("d" -> id) ~ ("n" -> noOp) ~ ("v" -> value)

  case NumberAddOperationData(id, noOp, value) =>
    ("t" -> OperationType.NumberAdd) ~ ("d" -> id) ~ ("n" -> noOp) ~ ("v" -> value)
  case NumberSetOperationData(id, noOp, value) =>
    ("t" -> OperationType.NumberValue) ~ ("d" -> id) ~ ("n" -> noOp) ~ ("v" -> value)

  case BooleanSetOperationData(id, noOp, value) =>
    ("t" -> OperationType.BooleanValue) ~ ("d" -> id) ~ ("n" -> noOp) ~ ("v" -> value)
}))