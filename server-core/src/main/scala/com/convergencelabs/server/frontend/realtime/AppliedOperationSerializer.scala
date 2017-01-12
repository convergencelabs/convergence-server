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

import AppliedOperationSerializer.D
import AppliedOperationSerializer.F
import AppliedOperationSerializer.I
import AppliedOperationSerializer.N
import AppliedOperationSerializer.O
import AppliedOperationSerializer.P
import AppliedOperationSerializer.T
import AppliedOperationSerializer.V
import AppliedOperationSerializer.L
import Utils.jnumberToDouble
import Utils.jnumberToInstant



object AppliedOperationSerializer {
  val T = "t"
  val V = "v"
  val D = "d"
  val N = "n"
  val I = "i"
  val P = "p"
  val O = "o"
  val F = "f"
  val L = "l"
}

class AppliedOperationSerializer extends CustomSerializer[AppliedOperationData](format => ({
  case JObject(List((T, JInt(Big(OperationType.Compound))), (O, JArray(ops)))) =>
    implicit val f = format;
    AppliedCompoundOperationData(ops.map { op => Extraction.extract[AppliedOperationData](op).asInstanceOf[AppliedDiscreteOperationData] })

  case JObject(List((T, JInt(Big(OperationType.ArrayInsert))), (D, JString(id)), (N, JBool(noOp)), (I, JInt(Big(index))), (V, value))) =>
    implicit val f = format;
    AppliedArrayInsertOperationData(id, noOp, index, Extraction.extract[DataValue](value))
  case JObject(List((T, JInt(Big(OperationType.ArrayRemove))), (D, JString(id)), (N, JBool(noOp)), (I, JInt(Big(index))), (O, oldValue))) =>
    implicit val f = format;
    AppliedArrayRemoveOperationData(id, noOp, index, Extraction.extract[Option[DataValue]](oldValue))
  case JObject(List((T, JInt(Big(OperationType.ArraySet))), (D, JString(id)), (N, JBool(noOp)), (I, JInt(Big(index))), (V, value), (O, oldValue))) => {
    implicit val f = format;
    AppliedArrayReplaceOperationData(id, noOp, index, Extraction.extract[DataValue](value), Option(oldValue) map { Extraction.extract[DataValue] })
  }
  case JObject(List((T, JInt(Big(OperationType.ArrayReorder))), (D, JString(id)), (N, JBool(noOp)), ("f", JInt(Big(from))), (O, JInt(Big(to))))) =>
    AppliedArrayMoveOperationData(id, noOp, from, to)
  case JObject(List((T, JInt(Big(OperationType.ArrayValue))), (D, JString(id)), (N, JBool(noOp)), (V, JArray(values)), (O, JArray(oldValues)))) => {
    implicit val f = format;
    AppliedArraySetOperationData(id, noOp, values.map { value => Extraction.extract[DataValue](value) }, Option(oldValues) map { _ map { Extraction.extract[DataValue] } })
  }
  case JObject(List((T, JInt(Big(OperationType.ObjectAdd))), (D, JString(id)), (N, JBool(noOp)), (P, JString(prop)), (V, value))) =>
    implicit val f = format;
    AppliedObjectAddPropertyOperationData(id, noOp, prop, Extraction.extract[DataValue](value))
  case JObject(List((T, JInt(Big(OperationType.ObjectSet))), (D, JString(id)), (N, JBool(noOp)), (P, JString(prop)), (V, value), (O, oldValue))) => {
    implicit val f = format;
    AppliedObjectSetPropertyOperationData(id, noOp, prop, Extraction.extract[DataValue](value), Option(oldValue) map { Extraction.extract[DataValue] })
  }
  case JObject(List((T, JInt(Big(OperationType.ObjectRemove))), (D, JString(id)), (N, JBool(noOp)), (P, JString(prop)), (O, oldValue))) => {
    implicit val f = format;
    AppliedObjectRemovePropertyOperationData(id, noOp, prop, Option(oldValue) map { Extraction.extract[DataValue] })
  }
  case JObject(List((T, JInt(Big(OperationType.ObjectValue))), (D, JString(id)), (N, JBool(noOp)), (V, JObject(fields)), (O, JObject(oldFields)))) =>
    implicit val f = format;
    AppliedObjectSetOperationData(id, noOp, fields.toMap.map { case (k, v) => (k, Extraction.extract[DataValue](v)) }, Option(fields) map {_.toMap.map { case (k, v) => (k, Extraction.extract[DataValue](v))} })

  case JObject(List((T, JInt(Big(OperationType.StringInsert))), (D, JString(id)), (N, JBool(noOp)), (I, JInt(Big(index))), (V, JString(value)))) =>
    AppliedStringInsertOperationData(id, noOp, index, value)
  case JObject(List((T, JInt(Big(OperationType.StringRemove))), (D, JString(id)), (N, JBool(noOp)), (I, JInt(Big(index))), (L, JInt(Big(length))), (V, JString(value)))) =>
    AppliedStringRemoveOperationData(id, noOp, index, length, Option(value))
  case JObject(List((T, JInt(Big(OperationType.StringValue))), (D, JString(id)), (N, JBool(noOp)), (V, JString(value)), (O, JString(oldValue)))) =>
    AppliedStringSetOperationData(id, noOp, value, Option(oldValue))
    
  case JObject(List((T, JInt(Big(OperationType.NumberAdd))), (D, JString(id)), (N, JBool(noOp)), (V, value))) =>
    AppliedNumberAddOperationData(id, noOp, jnumberToDouble(value))
  case JObject(List((T, JInt(Big(OperationType.NumberValue))), (D, JString(id)), (N, JBool(noOp)), (V, value), (O, oldValue))) =>
    AppliedNumberSetOperationData(id, noOp, jnumberToDouble(value), Option(oldValue) map { jnumberToDouble })
  
  case JObject(List((T, JInt(Big(OperationType.DateValue))), (D, JString(id)), (N, JBool(noOp)), (V, value), (O, oldValue))) =>
    AppliedDateSetOperationData(id, noOp, jnumberToInstant(value), Option(oldValue) map { jnumberToInstant })

  case JObject(List((T, JInt(Big(OperationType.BooleanValue))), (D, JString(id)), (N, JBool(noOp)), (V, JBool(value)), (O, JBool(oldValue)))) =>
    AppliedBooleanSetOperationData(id, noOp, value, Option(oldValue))
}, {
  case AppliedCompoundOperationData(ops) =>
    (T -> OperationType.Compound) ~
      (O -> ops.map { op =>
        Extraction.decompose(op)(format).asInstanceOf[JObject]
      })

  case AppliedArrayInsertOperationData(id, noOp, index, value) =>
    (T -> OperationType.ArrayInsert) ~ (D -> id) ~ (N -> noOp) ~ (I -> index) ~
      (V -> Extraction.decompose(value)(format).asInstanceOf[JObject])
  case AppliedArrayRemoveOperationData(id, noOp, index, oldValue) =>
    (T -> OperationType.ArrayRemove) ~ (D -> id) ~ (N -> noOp) ~ (I -> index) ~ (O -> oldValue.map(Extraction.decompose(_)(format).asInstanceOf[JObject]).getOrElse(null))
  case AppliedArrayReplaceOperationData(id, noOp, index, value, oldValue) =>
    (T -> OperationType.ArraySet) ~ (D -> id) ~ (N -> noOp) ~ (I -> index) ~
      (V -> Extraction.decompose(value)(format).asInstanceOf[JObject]) ~ (O -> oldValue.map(Extraction.decompose(_)(format).asInstanceOf[JObject]).getOrElse(null))
  case AppliedArrayMoveOperationData(id, noOp, from, to) =>
    (T -> OperationType.ArrayReorder) ~ (D -> id) ~ (N -> noOp) ~ (F -> from) ~ (O -> to)
  case AppliedArraySetOperationData(id, noOp, value, oldValue) =>
    (T -> OperationType.ArrayValue) ~ (D -> id) ~ (N -> noOp) ~
      (V -> Extraction.decompose(value)(format).asInstanceOf[JArray]) ~ (O -> oldValue.map(Extraction.decompose(_)(format).asInstanceOf[JArray]).getOrElse(null))

  case AppliedObjectSetPropertyOperationData(id, noOp, prop, value, oldValue) =>
    (T -> OperationType.ObjectSet) ~ (D -> id) ~ (N -> noOp) ~ (P -> prop) ~
      (V -> Extraction.decompose(value)(format).asInstanceOf[JObject])
  case AppliedObjectAddPropertyOperationData(id, noOp, prop, value) =>
    (T -> OperationType.ObjectAdd) ~ (D -> id) ~ (N -> noOp) ~ (P -> prop) ~
      (V -> Extraction.decompose(value)(format).asInstanceOf[JObject])
  case AppliedObjectRemovePropertyOperationData(id, noOp, prop, oldValue) =>
    (T -> OperationType.ObjectRemove) ~ (D -> id) ~ (N -> noOp) ~ (P -> prop) ~ (O -> oldValue.map(Extraction.decompose(_)(format).asInstanceOf[JObject]).getOrElse(null))
  case AppliedObjectSetOperationData(id, noOp, value, oldValue) =>
    (T -> OperationType.ObjectValue) ~ (D -> id) ~ (N -> noOp) ~
      (V -> Extraction.decompose(value)(format).asInstanceOf[JObject]) ~ (O -> oldValue.map(Extraction.decompose(_)(format).asInstanceOf[JObject]).getOrElse(null))

  case AppliedStringInsertOperationData(id, noOp, index, value) =>
    (T -> OperationType.StringInsert) ~ (D -> id) ~ (N -> noOp) ~ (I -> index) ~ (V -> value)
  case AppliedStringRemoveOperationData(id, noOp, index, length, oldValue) =>
    (T -> OperationType.StringRemove) ~ (D -> id) ~ (N -> noOp) ~ (I -> index) ~ (L -> length) ~ (O -> oldValue.getOrElse(null))
  case AppliedStringSetOperationData(id, noOp, value, oldValue) =>
    (T -> OperationType.StringValue) ~ (D -> id) ~ (N -> noOp) ~ (V -> value) ~ (O -> oldValue.getOrElse(null))

  case AppliedNumberAddOperationData(id, noOp, value) =>
    (T -> OperationType.NumberAdd) ~ (D -> id) ~ (N -> noOp) ~ (V -> value)
  case AppliedNumberSetOperationData(id, noOp, value, oldValue) =>
    (T -> OperationType.NumberValue) ~ (D -> id) ~ (N -> noOp) ~ (V -> value) ~ (O -> oldValue.map(double2jvalue).getOrElse(null))
    
  case AppliedDateSetOperationData(id, noOp, value, oldValue) =>
    (T -> OperationType.DateValue) ~ (D -> id) ~ (N -> noOp) ~ (V -> value.getEpochSecond) ~ (O -> oldValue.map(_.getEpochSecond).map(long2jvalue).getOrElse(null))

  case AppliedBooleanSetOperationData(id, noOp, value, oldValue) =>
    (T -> OperationType.BooleanValue) ~ (D -> id) ~ (N -> noOp) ~ (V -> value) ~ (O -> oldValue.map(boolean2jvalue).getOrElse(null))
}))
