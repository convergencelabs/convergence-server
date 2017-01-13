package com.convergencelabs.server.frontend.realtime

import org.json4s.CustomSerializer
import org.json4s.Extraction
import org.json4s.JArray
import org.json4s.JBool
import org.json4s.JDouble
import org.json4s.JInt
import org.json4s.JLong
import org.json4s.JObject
import org.json4s.JString
import org.json4s.JsonAST.JValue
import org.json4s.JsonDSL.boolean2jvalue
import org.json4s.JsonDSL.double2jvalue
import org.json4s.JsonDSL.int2jvalue
import org.json4s.JsonDSL.jobject2assoc
import org.json4s.JsonDSL.pair2Assoc
import org.json4s.JsonDSL.pair2jvalue
import org.json4s.JsonDSL.seq2jvalue
import org.json4s.JsonDSL.string2jvalue
import org.json4s.JsonDSL.long2jvalue

import com.convergencelabs.server.domain.model.data.DataValue
import com.convergencelabs.server.frontend.realtime.model.OperationType

import OperationSerializer.D
import OperationSerializer.F
import OperationSerializer.I
import OperationSerializer.N
import OperationSerializer.O
import OperationSerializer.P
import OperationSerializer.T
import OperationSerializer.V
import Utils.jnumberToDouble
import Utils.jnumberToInstant
import java.time.Instant

object Utils {
  def jnumberToDouble(value: JValue): Double = {
    value match {
      case JInt(x)    => x.doubleValue()
      case JDouble(x) => x
      case JLong(x)   => x.doubleValue()
      case _          => throw new IllegalArgumentException("invlid number type")
    }
  }

  def jnumberToInstant(value: JValue): Instant = {
    value match {
      case JInt(x) => Instant.ofEpochMilli(x.longValue())
      case JLong(x) => Instant.ofEpochMilli(x.longValue())
      case _        => throw new IllegalArgumentException("invlid date type")
    }
  }
}

object Big {
  def unapply(n: BigInt): Option[Int] = Some(n.toInt)
}

object OperationSerializer {
  val T = "t"
  val V = "v"
  val D = "d"
  val N = "n"
  val I = "i"
  val P = "p"
  val O = "o"
  val F = "f"
}

class OperationSerializer extends CustomSerializer[OperationData](format => ({
  case JObject(List((T, JInt(Big(OperationType.Compound))), (O, JArray(ops)))) =>
    implicit val f = format;
    CompoundOperationData(ops.map { op => Extraction.extract[OperationData](op).asInstanceOf[DiscreteOperationData] })

  case JObject(List((T, JInt(Big(OperationType.ArrayInsert))), (D, JString(id)), (N, JBool(noOp)), (I, JInt(Big(index))), (V, value))) =>
    implicit val f = format;
    ArrayInsertOperationData(id, noOp, index, Extraction.extract[DataValue](value))
  case JObject(List((T, JInt(Big(OperationType.ArrayRemove))), (D, JString(id)), (N, JBool(noOp)), (I, JInt(Big(index))))) =>
    ArrayRemoveOperationData(id, noOp, index)
  case JObject(List((T, JInt(Big(OperationType.ArraySet))), (D, JString(id)), (N, JBool(noOp)), (I, JInt(Big(index))), (V, value))) =>
    implicit val f = format;
    ArrayReplaceOperationData(id, noOp, index, Extraction.extract[DataValue](value))
  case JObject(List((T, JInt(Big(OperationType.ArrayReorder))), (D, JString(id)), (N, JBool(noOp)), ("f", JInt(Big(from))), (O, JInt(Big(to))))) =>
    ArrayMoveOperationData(id, noOp, from, to)
  case JObject(List((T, JInt(Big(OperationType.ArrayValue))), (D, JString(id)), (N, JBool(noOp)), (V, JArray(values)))) =>
    implicit val f = format;
    ArraySetOperationData(id, noOp, values.map { value => Extraction.extract[DataValue](value) })

  case JObject(List((T, JInt(Big(OperationType.ObjectAdd))), (D, JString(id)), (N, JBool(noOp)), (P, JString(prop)), (V, value))) =>
    implicit val f = format;
    ObjectAddPropertyOperationData(id, noOp, prop, Extraction.extract[DataValue](value))
  case JObject(List((T, JInt(Big(OperationType.ObjectSet))), (D, JString(id)), (N, JBool(noOp)), (P, JString(prop)), (V, value))) =>
    implicit val f = format;
    ObjectSetPropertyOperationData(id, noOp, prop, Extraction.extract[DataValue](value))
  case JObject(List((T, JInt(Big(OperationType.ObjectRemove))), (D, JString(id)), (N, JBool(noOp)), (P, JString(prop)))) =>
    ObjectRemovePropertyOperationData(id, noOp, prop)
  case JObject(List((T, JInt(Big(OperationType.ObjectValue))), (D, JString(id)), (N, JBool(noOp)), (V, JObject(fields)))) =>
    implicit val f = format;
    ObjectSetOperationData(id, noOp, fields.toMap.map { case (k, v) => (k, Extraction.extract[DataValue](v)) })

  case JObject(List((T, JInt(Big(OperationType.StringInsert))), (D, JString(id)), (N, JBool(noOp)), (I, JInt(Big(index))), (V, JString(value)))) =>
    StringInsertOperationData(id, noOp, index, value)
  case JObject(List((T, JInt(Big(OperationType.StringRemove))), (D, JString(id)), (N, JBool(noOp)), (I, JInt(Big(index))), (V, JString(value)))) =>
    StringRemoveOperationData(id, noOp, index, value)
  case JObject(List((T, JInt(Big(OperationType.StringValue))), (D, JString(id)), (N, JBool(noOp)), (V, JString(value)))) =>
    StringSetOperationData(id, noOp, value)

  case JObject(List((T, JInt(Big(OperationType.NumberAdd))), (D, JString(id)), (N, JBool(noOp)), (V, value))) =>
    NumberAddOperationData(id, noOp, jnumberToDouble(value))
  case JObject(List((T, JInt(Big(OperationType.NumberValue))), (D, JString(id)), (N, JBool(noOp)), (V, value))) =>
    NumberSetOperationData(id, noOp, jnumberToDouble(value))

  case JObject(List((T, JInt(Big(OperationType.DateValue))), (D, JString(id)), (N, JBool(noOp)), (V, value))) =>
    DateSetOperationData(id, noOp, jnumberToInstant(value))

  case JObject(List((T, JInt(Big(OperationType.BooleanValue))), (D, JString(id)), (N, JBool(noOp)), (V, JBool(value)))) =>
    BooleanSetOperationData(id, noOp, value)
}, {
  case CompoundOperationData(ops) =>
    (T -> OperationType.Compound) ~
      (O -> ops.map { op =>
        Extraction.decompose(op)(format).asInstanceOf[JObject]
      })

  case ArrayInsertOperationData(id, noOp, index, value) =>
    (T -> OperationType.ArrayInsert) ~ (D -> id) ~ (N -> noOp) ~ (I -> index) ~
      (V -> Extraction.decompose(value)(format).asInstanceOf[JObject])
  case ArrayRemoveOperationData(id, noOp, index) =>
    (T -> OperationType.ArrayRemove) ~ (D -> id) ~ (N -> noOp) ~ (I -> index)
  case ArrayReplaceOperationData(id, noOp, index, value) =>
    (T -> OperationType.ArraySet) ~ (D -> id) ~ (N -> noOp) ~ (I -> index) ~
      (V -> Extraction.decompose(value)(format).asInstanceOf[JObject])
  case ArrayMoveOperationData(id, noOp, from, to) =>
    (T -> OperationType.ArrayReorder) ~ (D -> id) ~ (N -> noOp) ~ (F -> from) ~ (O -> to)
  case ArraySetOperationData(id, noOp, value) =>
    (T -> OperationType.ArrayValue) ~ (D -> id) ~ (N -> noOp) ~
      (V -> Extraction.decompose(value)(format).asInstanceOf[JArray])

  case ObjectSetPropertyOperationData(id, noOp, prop, value) =>
    (T -> OperationType.ObjectSet) ~ (D -> id) ~ (N -> noOp) ~ (P -> prop) ~
      (V -> Extraction.decompose(value)(format).asInstanceOf[JObject])
  case ObjectAddPropertyOperationData(id, noOp, prop, value) =>
    (T -> OperationType.ObjectAdd) ~ (D -> id) ~ (N -> noOp) ~ (P -> prop) ~
      (V -> Extraction.decompose(value)(format).asInstanceOf[JObject])
  case ObjectRemovePropertyOperationData(id, noOp, prop) =>
    (T -> OperationType.ObjectRemove) ~ (D -> id) ~ (N -> noOp) ~ (P -> prop)
  case ObjectSetOperationData(id, noOp, value) =>
    (T -> OperationType.ObjectValue) ~ (D -> id) ~ (N -> noOp) ~
      (V -> Extraction.decompose(value)(format).asInstanceOf[JObject])

  case StringInsertOperationData(id, noOp, index, value) =>
    (T -> OperationType.StringInsert) ~ (D -> id) ~ (N -> noOp) ~ (I -> index) ~ (V -> value)
  case StringRemoveOperationData(id, noOp, index, value) =>
    (T -> OperationType.StringRemove) ~ (D -> id) ~ (N -> noOp) ~ (I -> index) ~ (V -> value)
  case StringSetOperationData(id, noOp, value) =>
    (T -> OperationType.StringValue) ~ (D -> id) ~ (N -> noOp) ~ (V -> value)

  case NumberAddOperationData(id, noOp, value) =>
    (T -> OperationType.NumberAdd) ~ (D -> id) ~ (N -> noOp) ~ (V -> value)
  case NumberSetOperationData(id, noOp, value) =>
    (T -> OperationType.NumberValue) ~ (D -> id) ~ (N -> noOp) ~ (V -> value)

  case DateSetOperationData(id, noOp, value) =>
    (T -> OperationType.DateValue) ~ (D -> id) ~ (N -> noOp) ~ (V -> value.toEpochMilli())

  case BooleanSetOperationData(id, noOp, value) =>
    (T -> OperationType.BooleanValue) ~ (D -> id) ~ (N -> noOp) ~ (V -> value)
}))
