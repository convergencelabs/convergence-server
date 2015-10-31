package com.convergencelabs.server.datastore.domain

import scala.collection.JavaConversions._
import scala.collection.JavaConverters._
import com.convergencelabs.server.domain.model.ot.ops._
import com.convergencelabs.server.frontend.realtime.proto._
import org.json4s.JValue
import org.json4s.NoTypeHints
import org.json4s.jackson.Serialization
import org.json4s._
import org.json4s.JValue
import org.json4s.NoTypeHints
import org.json4s.jackson.Serialization
import org.json4s._
import org.json4s.jackson.JsonMethods._
import org.json4s.jackson.Serialization._
import org.json4s.JsonAST.JNumber
import java.util.{ HashMap, Map => JavaMap, List => JavaList }
import com.convergencelabs.server.util.JValueMapper

object OrientDBOperationMapper {

  object OperationType {
    val Compound = "c"

    val StringInsert = "si"
    val StringRemove = "sr"
    val StringSet = "ss"

    val ArrayInsert = "ai"
    val ArrayRemove = "ar"
    val ArrayMove = "am"
    val ArrayReplace = "ap"
    val ArraySet = "as"

    val ObjectSetProperty = "ot"
    val ObjectAddProperty = "oa"
    val ObjectRemoveProperty = "or"
    val ObjectSet = "os"

    val NumberAdd = "na"
    val NumberSet = "ns"
  }

  object Fields {
    val Path = "path"
    val Type = "type"
    val Prop = "prop"
    val NoOp = "noOp"
    val Ops = "ops"
    val Val = "val"
    val From = "from"
    val To = "to"
    val Idx = "idx"
    val Delta = "delta"
  }

  def mapToOperation(opAsMap: JavaMap[String, _]): Operation = {
    val t = opAsMap.get(Fields.Type).asInstanceOf[String]
    t match {
      case OperationType.Compound => mapToCompoundOperation(opAsMap)
      case _ => mapToDiscreteOperation(opAsMap)
    }
  }

  private[this] def mapToCompoundOperation(opAsMap: JavaMap[String, _]): CompoundOperation = {
    val opMaps = opAsMap.get(Fields.Ops).asInstanceOf[JavaList[JavaMap[String, Any]]]
    val ops = opMaps.asScala.toList.map(opMap => mapToDiscreteOperation(opMap))
    CompoundOperation(ops)
  }

  private[this] def mapToDiscreteOperation(opAsMap: JavaMap[String, _]): DiscreteOperation = {
    val t = opAsMap.get(Fields.Type).asInstanceOf[String]
    t match {
      case OperationType.StringInsert => mapToStringInsert(opAsMap)
      case OperationType.StringRemove => mapToStringRemove(opAsMap)
      case OperationType.StringSet => mapToStringSet(opAsMap)

      case OperationType.ArrayInsert => mapToArrayInsert(opAsMap)
      case OperationType.ArrayRemove => mapToArrayRemove(opAsMap)
      case OperationType.ArrayMove => mapToArrayMove(opAsMap)
      case OperationType.ArrayReplace => mapToArrayReplace(opAsMap)
      case OperationType.ArraySet => mapToArraySet(opAsMap)

      case OperationType.ObjectSetProperty => mapToObjectSetProperty(opAsMap)
      case OperationType.ObjectAddProperty => mapToObjectAddProperty(opAsMap)
      case OperationType.ObjectRemoveProperty => mapToObjectRemoveProperty(opAsMap)
      case OperationType.ObjectSet => mapToObjectSet(opAsMap)

      case OperationType.NumberAdd => mapToNumberAdd(opAsMap)
      case OperationType.NumberSet => mapToNumberSet(opAsMap)
    }
  }

  private[this] def mapToStringInsert(map: JavaMap[String, _]): StringInsertOperation = {
    val path = map.get(Fields.Path).asInstanceOf[JavaList[_]]
    val noOp = map.get(Fields.NoOp).asInstanceOf[Boolean]
    val index = map.get(Fields.Idx).asInstanceOf[Int]
    val value = map.get(Fields.Val).asInstanceOf[String]
    StringInsertOperation(path.asScala.toList, noOp, index, value)
  }

  private[this] def mapToStringRemove(map: JavaMap[String, _]): StringRemoveOperation = {
    val path = map.get(Fields.Path).asInstanceOf[JavaList[_]]
    val noOp = map.get(Fields.NoOp).asInstanceOf[Boolean]
    val index = map.get(Fields.Idx).asInstanceOf[Int]
    val value = map.get(Fields.Val).asInstanceOf[String]
    StringRemoveOperation(path.asScala.toList, noOp, index, value)
  }

  private[this] def mapToStringSet(map: JavaMap[String, _]): StringSetOperation = {
    val path = map.get(Fields.Path).asInstanceOf[JavaList[_]]
    val noOp = map.get(Fields.NoOp).asInstanceOf[Boolean]
    val value = map.get(Fields.Val).asInstanceOf[String]
    StringSetOperation(path.asScala.toList, noOp, value)
  }

  private[this] def mapToArrayInsert(map: JavaMap[String, _]): ArrayInsertOperation = {
    val path = map.get(Fields.Path).asInstanceOf[JavaList[_]]
    val noOp = map.get(Fields.NoOp).asInstanceOf[Boolean]
    val idx = map.get(Fields.Idx).asInstanceOf[Int]
    val value = map.get(Fields.Val)
    ArrayInsertOperation(path.asScala.toList, noOp, idx, javaToJValue(value))
  }

  private[this] def mapToArrayRemove(map: JavaMap[String, _]): ArrayRemoveOperation = {
    val path = map.get(Fields.Path).asInstanceOf[JavaList[_]]
    val noOp = map.get(Fields.NoOp).asInstanceOf[Boolean]
    val idx = map.get(Fields.Idx).asInstanceOf[Int]
    ArrayRemoveOperation(path.asScala.toList, noOp, idx)
  }

  private[this] def mapToArrayMove(map: JavaMap[String, _]): ArrayMoveOperation = {
    val path = map.get(Fields.Path).asInstanceOf[JavaList[_]]
    val noOp = map.get(Fields.NoOp).asInstanceOf[Boolean]
    val from = map.get(Fields.From).asInstanceOf[Int]
    val to = map.get(Fields.To).asInstanceOf[Int]
    ArrayMoveOperation(path.asScala.toList, noOp, from, to)
  }

  private[this] def mapToArrayReplace(map: JavaMap[String, _]): ArrayReplaceOperation = {
    val path = map.get(Fields.Path).asInstanceOf[JavaList[_]]
    val noOp = map.get(Fields.NoOp).asInstanceOf[Boolean]
    val idx = map.get(Fields.Idx).asInstanceOf[Int]
    ArrayReplaceOperation(path.asScala.toList, noOp, idx, javaToJValue(map.get(Fields.Val)))
  }

  private[this] def mapToArraySet(map: JavaMap[String, _]): ArraySetOperation = {
    val path = map.get(Fields.Path).asInstanceOf[JavaList[_]]
    val noOp = map.get(Fields.NoOp).asInstanceOf[Boolean]
    val value: JavaList[_] = map.get(Fields.Val).asInstanceOf[JavaList[_]]
    ArraySetOperation(path.asScala.toList, noOp, javaToJValue(value).asInstanceOf[JArray])
  }

  private[this] def mapToObjectSetProperty(map: JavaMap[String, _]): ObjectSetPropertyOperation = {
    val path = map.get(Fields.Path).asInstanceOf[JavaList[_]]
    val noOp = map.get(Fields.NoOp).asInstanceOf[Boolean]
    val prop = map.get(Fields.Prop).asInstanceOf[String]
    val value = map.get(Fields.Val)
    ObjectSetPropertyOperation(path.asScala.toList, noOp, prop, javaToJValue(value))
  }

  private[this] def mapToObjectAddProperty(map: JavaMap[String, _]): ObjectAddPropertyOperation = {
    val path = map.get(Fields.Path).asInstanceOf[JavaList[_]]
    val noOp = map.get(Fields.NoOp).asInstanceOf[Boolean]
    val prop = map.get(Fields.Prop).asInstanceOf[String]
    val value = map.get(Fields.Val)
    ObjectAddPropertyOperation(path.asScala.toList, noOp, prop, javaToJValue(value))
  }

  private[this] def mapToObjectRemoveProperty(map: JavaMap[String, _]): ObjectRemovePropertyOperation = {
    val path = map.get(Fields.Path).asInstanceOf[JavaList[_]]
    val noOp = map.get(Fields.NoOp).asInstanceOf[Boolean]
    val prop = map.get(Fields.Prop).asInstanceOf[String]
    ObjectRemovePropertyOperation(path.asScala.toList, noOp, prop)
  }

  private[this] def mapToObjectSet(map: JavaMap[String, _]): ObjectSetOperation = {
    val path = map.get(Fields.Path).asInstanceOf[JavaList[_]]
    val noOp = map.get(Fields.NoOp).asInstanceOf[Boolean]
    val value = map.get(Fields.Val)
    ObjectSetOperation(path.asScala.toList, noOp, javaToJValue(value).asInstanceOf[JObject])
  }

  private[this] def mapToNumberAdd(map: JavaMap[String, _]): NumberAddOperation = {
    val path = map.get(Fields.Path).asInstanceOf[JavaList[_]]
    val noOp = map.get(Fields.NoOp).asInstanceOf[Boolean]
    val delta = map.get(Fields.Delta).asInstanceOf[String]
    NumberAddOperation(path.asScala.toList, noOp, javaToJValue(delta).asInstanceOf[JNumber])
  }

  private[this] def mapToNumberSet(map: JavaMap[String, _]): NumberSetOperation = {
    val path = map.get(Fields.Path).asInstanceOf[JavaList[_]]
    val noOp = map.get(Fields.NoOp).asInstanceOf[Boolean]
    val value = map.get("value").asInstanceOf[String]
    NumberSetOperation(path.asScala.toList, noOp, javaToJValue(value).asInstanceOf[JNumber])
  }

  def operationToMap(op: Operation): JavaMap[String, _] = {
    op match {
      case operation: CompoundOperation => compoundOperationToMap(operation)
      case operation: DiscreteOperation => discreteOperationToMap(operation)
    }
  }

  private[this] def compoundOperationToMap(op: CompoundOperation): JavaMap[String, Object] = {
    val ops = op.operations.map(discreteOp => operationToMap(discreteOp)).asJava
    val result = new HashMap[String, Object]()
    result.put(Fields.Type, OperationType.Compound)
    result.put(Fields.Ops, ops)
    result
  }

  // FIXME is going to a scalamap and then to a javamap somewhat a waste from a performance standpoint?
  private[this] def discreteOperationToMap(op: DiscreteOperation): JavaMap[String, _] = {
    op match {

      // String Operations
      case StringInsertOperation(path, noOp, index, value) =>
        Map(Fields.Type -> OperationType.StringInsert, Fields.Path -> path.asJava, Fields.NoOp -> noOp, Fields.Idx -> index, Fields.Val -> value).asJava

      case StringRemoveOperation(path, noOp, index, value) =>
        Map(Fields.Type -> OperationType.StringRemove, Fields.Path -> path.asJava, Fields.NoOp -> noOp, Fields.Idx -> index, Fields.Val -> value).asJava

      case StringSetOperation(path, noOp, value) =>
        Map(Fields.Type -> OperationType.StringSet, Fields.Path -> path.asJava, Fields.NoOp -> noOp, Fields.Val -> value).asJava

      // Array Operations
      case ArrayInsertOperation(path, noOp, idx, value) =>
        Map(Fields.Type -> OperationType.ArrayInsert, Fields.Path -> path.asJava, Fields.NoOp -> noOp, Fields.Idx -> idx, Fields.Val -> jValueToJava(value)).asJava

      case ArrayRemoveOperation(path, noOp, idx) =>
        Map(Fields.Type -> OperationType.ArrayRemove, Fields.Path -> path.asJava, Fields.NoOp -> noOp, Fields.Idx -> idx).asJava

      case ArrayMoveOperation(path, noOp, fromIdx, toIdx) =>
        Map(Fields.Type -> OperationType.ArrayMove, Fields.Path -> path.asJava, Fields.NoOp -> noOp, Fields.From -> fromIdx, Fields.To -> toIdx).asJava

      case ArrayReplaceOperation(path, noOp, idx, value) =>
        Map(Fields.Type -> OperationType.ArrayReplace, Fields.Path -> path.asJava, Fields.NoOp -> noOp, Fields.Idx -> idx, Fields.Val -> jValueToJava(value)).asJava

      case ArraySetOperation(path, noOp, value) =>
        Map(Fields.Type -> OperationType.ArraySet, Fields.Path -> path.asJava, Fields.NoOp -> noOp, Fields.Val -> jValueToJava(value)).asJava

      // Object Operations
      case ObjectSetPropertyOperation(path, noOp, prop, value) =>
        Map(Fields.Type -> OperationType.ObjectSetProperty, Fields.Path -> path.asJava, Fields.NoOp -> noOp, Fields.Prop -> prop, Fields.Val -> jValueToJava(value)).asJava

      case ObjectAddPropertyOperation(path, noOp, prop, value) =>
        Map(Fields.Type -> OperationType.ObjectAddProperty, Fields.Path -> path.asJava, Fields.NoOp -> noOp, Fields.Prop -> prop, Fields.Val -> jValueToJava(value)).asJava

      case ObjectRemovePropertyOperation(path, noOp, prop) =>
        Map(Fields.Type -> OperationType.ObjectRemoveProperty, Fields.Path -> path.asJava, Fields.NoOp -> noOp, Fields.Prop -> prop).asJava

      case ObjectSetOperation(path, noOp, value) =>
        Map(Fields.Type -> OperationType.ObjectSet, Fields.Path -> path.asJava, Fields.NoOp -> noOp, Fields.Val -> jValueToJava(value)).asJava

      // Number Operations
      case NumberAddOperation(path, noOp, delta) =>
        Map(Fields.Type -> OperationType.NumberAdd, Fields.Path -> path.asJava, Fields.NoOp -> noOp, Fields.Delta -> delta).asJava

      case NumberSetOperation(path, noOp, value) =>
        Map(Fields.Type -> OperationType.NumberSet, Fields.Path -> path.asJava, Fields.NoOp -> noOp, Fields.Val -> jValueToJava(value.asInstanceOf[JValue])).asJava
    }
  }

  private[this] def jValueToJava(jValue: JValue): Any = JValueMapper.jValueToJava(jValue)

  private[this] def javaToJValue(value: Any): JValue = JValueMapper.javaToJValue(value)
}