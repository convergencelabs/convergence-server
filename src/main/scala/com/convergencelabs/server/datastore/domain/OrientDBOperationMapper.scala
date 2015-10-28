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

  def mapToOperation(opAsMap: JavaMap[String, _]): Operation = {
    val t = opAsMap.get("type").asInstanceOf[String]
    t match {
      case "c" => mapToCompoundOperation(opAsMap)
      case _ => mapToDiscreteOperation(opAsMap)
    }
  }

  private[this] def mapToCompoundOperation(opAsMap: JavaMap[String, _]): CompoundOperation = {
    val opMaps = opAsMap.get("ops").asInstanceOf[JavaList[JavaMap[String, Any]]]
    val ops = opMaps.asScala.toList.map(opMap => mapToDiscreteOperation(opMap))
    CompoundOperation(ops)
  }

  private[this] def mapToDiscreteOperation(opAsMap: JavaMap[String, _]): DiscreteOperation = {
    val t = opAsMap.get("type").asInstanceOf[String]
    t match {
      case "si" => mapToStringInsert(opAsMap)
      case "sr" => mapToStringRemove(opAsMap)
      case "ss" => mapToStringSet(opAsMap)

      case "ai" => mapToArrayInsert(opAsMap)
      case "ar" => mapToArrayRemove(opAsMap)
      case "am" => mapToArrayMove(opAsMap)
      case "ap" => mapToArrayReplace(opAsMap)
      case "as" => mapToArraySet(opAsMap)

      case "ot" => mapToObjectSetProperty(opAsMap)
      case "oa" => mapToObjectAddProperty(opAsMap)
      case "or" => mapToObjectRemoveProperty(opAsMap)
      case "os" => mapToObjectSet(opAsMap)

      case "na" => mapToNumberAdd(opAsMap)
      case "ns" => mapToNumberSet(opAsMap)
    }
  }

  private[this] def mapToStringInsert(map: JavaMap[String, _]): StringInsertOperation = {
    val path = map.get("path").asInstanceOf[JavaList[_]]
    val noOp = map.get("noOp").asInstanceOf[Boolean]
    val index = map.get("idx").asInstanceOf[Int]
    val value = map.get("val").asInstanceOf[String]
    StringInsertOperation(path.asScala.toList, noOp, index, value)
  }

  private[this] def mapToStringRemove(map: JavaMap[String, _]): StringRemoveOperation = {
    val path = map.get("path").asInstanceOf[JavaList[_]]
    val noOp = map.get("noOp").asInstanceOf[Boolean]
    val index = map.get("idx").asInstanceOf[Int]
    val value = map.get("val").asInstanceOf[String]
    StringRemoveOperation(path.asScala.toList, noOp, index, value)
  }

  private[this] def mapToStringSet(map: JavaMap[String, _]): StringSetOperation = {
    val path = map.get("path").asInstanceOf[JavaList[_]]
    val noOp = map.get("noOp").asInstanceOf[Boolean]
    val value = map.get("val").asInstanceOf[String]
    StringSetOperation(path.asScala.toList, noOp, value)
  }

  private[this] def mapToArrayInsert(map: JavaMap[String, _]): ArrayInsertOperation = {
    val path = map.get("path").asInstanceOf[JavaList[_]]
    val noOp = map.get("noOp").asInstanceOf[Boolean]
    val idx = map.get("idx").asInstanceOf[Int]
    val value = map.get("val")
    ArrayInsertOperation(path.asScala.toList, noOp, idx, javaToJValue(value))
  }

  private[this] def mapToArrayRemove(map: JavaMap[String, _]): ArrayRemoveOperation = {
    val path = map.get("path").asInstanceOf[JavaList[_]]
    val noOp = map.get("noOp").asInstanceOf[Boolean]
    val idx = map.get("idx").asInstanceOf[Int]
    ArrayRemoveOperation(path.asScala.toList, noOp, idx)
  }

  private[this] def mapToArrayMove(map: JavaMap[String, _]): ArrayMoveOperation = {
    val path = map.get("path").asInstanceOf[JavaList[_]]
    val noOp = map.get("noOp").asInstanceOf[Boolean]
    val from = map.get("from").asInstanceOf[Int]
    val to = map.get("to").asInstanceOf[Int]
    ArrayMoveOperation(path.asScala.toList, noOp, from, to)
  }

  private[this] def mapToArrayReplace(map: JavaMap[String, _]): ArrayReplaceOperation = {
    val path = map.get("path").asInstanceOf[JavaList[_]]
    val noOp = map.get("noOp").asInstanceOf[Boolean]
    val idx = map.get("idx").asInstanceOf[Int]
    ArrayReplaceOperation(path.asScala.toList, noOp, idx, javaToJValue(map.get("val")))
  }

  private[this] def mapToArraySet(map: JavaMap[String, _]): ArraySetOperation = {
    val path = map.get("path").asInstanceOf[JavaList[_]]
    val noOp = map.get("noOp").asInstanceOf[Boolean]
    val value: JavaList[_] = map.get("val").asInstanceOf[JavaList[_]]
    ArraySetOperation(path.asScala.toList, noOp, javaToJValue(value).asInstanceOf[JArray])
  }

  private[this] def mapToObjectSetProperty(map: JavaMap[String, _]): ObjectSetPropertyOperation = {
    val path = map.get("path").asInstanceOf[JavaList[_]]
    val noOp = map.get("noOp").asInstanceOf[Boolean]
    val prop = map.get("prop").asInstanceOf[String]
    val value = map.get("val")
    ObjectSetPropertyOperation(path.asScala.toList, noOp, prop, javaToJValue(value))
  }

  private[this] def mapToObjectAddProperty(map: JavaMap[String, _]): ObjectAddPropertyOperation = {
    val path = map.get("path").asInstanceOf[JavaList[_]]
    val noOp = map.get("noOp").asInstanceOf[Boolean]
    val prop = map.get("prop").asInstanceOf[String]
    val value = map.get("val")
    ObjectAddPropertyOperation(path.asScala.toList, noOp, prop, javaToJValue(value))
  }

  private[this] def mapToObjectRemoveProperty(map: JavaMap[String, _]): ObjectRemovePropertyOperation = {
    val path = map.get("path").asInstanceOf[JavaList[_]]
    val noOp = map.get("noOp").asInstanceOf[Boolean]
    val prop = map.get("prop").asInstanceOf[String]
    ObjectRemovePropertyOperation(path.asScala.toList, noOp, prop)
  }

  private[this] def mapToObjectSet(map: JavaMap[String, _]): ObjectSetOperation = {
    val path = map.get("path").asInstanceOf[JavaList[_]]
    val noOp = map.get("noOp").asInstanceOf[Boolean]
    val value = map.get("val").asInstanceOf[String]
    ObjectSetOperation(path.asScala.toList, noOp, javaToJValue(value).asInstanceOf[JObject])
  }

  private[this] def mapToNumberAdd(map: JavaMap[String, _]): NumberAddOperation = {
    val path = map.get("path").asInstanceOf[JavaList[_]]
    val noOp = map.get("noOp").asInstanceOf[Boolean]
    val delta = map.get("delta").asInstanceOf[String]
    NumberAddOperation(path.asScala.toList, noOp, javaToJValue(delta).asInstanceOf[JNumber])
  }

  private[this] def mapToNumberSet(map: JavaMap[String, _]): NumberSetOperation = {
    val path = map.get("path").asInstanceOf[JavaList[_]]
    val noOp = map.get("noOp").asInstanceOf[Boolean]
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
    result.put("type", "compound")
    result.put("ops", ops)
    result
  }

  // FIXME is going to a scalamap and then to a javamap somewhat a waste from a performance standpoint?
  private[this] def discreteOperationToMap(op: DiscreteOperation): JavaMap[String, _] = {
    op match {

      // String Operations
      case StringInsertOperation(path, noOp, index, value) =>
        Map("type" -> "si", "path" -> path.asJava, "noOp" -> noOp, "idx" -> index, "val" -> value).asJava

      case StringRemoveOperation(path, noOp, index, value) =>
        Map("type" -> "sr", "path" -> path.asJava, "noOp" -> noOp, "idx" -> index, "val" -> value).asJava

      case StringSetOperation(path, noOp, value) =>
        Map("type" -> "ss", "path" -> path.asJava, "noOp" -> noOp, "val" -> value).asJava

      // Array Operations
      case ArrayInsertOperation(path, noOp, idx, value) =>
        Map("type" -> "ai", "path" -> path.asJava, "noOp" -> noOp, "idx" -> idx, "val" -> jValueToJava(value)).asJava

      case ArrayRemoveOperation(path, noOp, idx) =>
        Map("type" -> "ar", "path" -> path.asJava, "noOp" -> noOp, "idx" -> idx).asJava

      case ArrayMoveOperation(path, noOp, fromIdx, toIdx) =>
        Map("type" -> "am", "path" -> path.asJava, "noOp" -> noOp, "from" -> fromIdx, "to" -> toIdx).asJava

      case ArrayReplaceOperation(path, noOp, idx, value) =>
        Map("type" -> "ap", "path" -> path.asJava, "noOp" -> noOp, "idx" -> idx, "val" -> jValueToJava(value)).asJava

      case ArraySetOperation(path, noOp, value) =>
        Map("type" -> "as", "path" -> path.asJava, "noOp" -> noOp, "val" -> jValueToJava(value)).asJava

      // Object Operations
      case ObjectSetPropertyOperation(path, noOp, prop, value) =>
        Map("type" -> "ot", "path" -> path.asJava, "noOp" -> noOp, "val" -> jValueToJava(value)).asJava

      case ObjectAddPropertyOperation(path, noOp, prop, value) =>
        Map("type" -> "oa", "path" -> path.asJava, "noOp" -> noOp, "prop" -> prop, "val" -> jValueToJava(value)).asJava

      case ObjectRemovePropertyOperation(path, noOp, prop) =>
        Map("type" -> "or", "path" -> path.asJava, "noOp" -> noOp, "prop" -> prop).asJava

      case ObjectSetOperation(path, noOp, value) =>
        Map("type" -> "os", "path" -> path.asJava, "noOp" -> noOp, "val" -> jValueToJava(value)).asJava

      // Number Operations
      case NumberAddOperation(path, noOp, delta) =>
        Map("type" -> "na", "path" -> path.asJava, "noOp" -> noOp, "delta" -> delta).asJava

      case NumberSetOperation(path, noOp, value) =>
        Map("type" -> "ns", "path" -> path.asJava, "noOp" -> noOp, "val" -> jValueToJava(value.asInstanceOf[JValue])).asJava
    }
  }

  private[this] def jValueToJava(jValue: JValue): Any = JValueMapper.jValueToJava(jValue)

  private[this] def javaToJValue(value: Any): JValue = JValueMapper.javaToJValue(value)
}