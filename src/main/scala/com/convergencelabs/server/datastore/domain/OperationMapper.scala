package com.convergencelabs.server.frontend.realtime

import scala.collection.JavaConversions._
import scala.collection.JavaConverters._
import com.convergencelabs.server.domain.model.ot.ops._
import com.convergencelabs.server.frontend.realtime.proto._
import java.util.HashMap

import org.json4s.JValue
import org.json4s.NoTypeHints
import org.json4s.jackson.Serialization
import org.json4s._
import org.json4s.jackson.JsonMethods._
import org.json4s.jackson.Serialization._

object OrientOperationMapper {

  type JavaMap[K, V] = java.util.Map[K, V]
  type JavaList[V] = java.util.List[V]

  def mapToOperation(opAsMap: JavaMap[String, _]): Operation = {
    val t = opAsMap.get("type").asInstanceOf[String]
    t match {
      case "c" => mapToCompoundOperation(opAsMap)
      case _ => mapToDiscreteOperation(opAsMap)
    }
  }

  def mapToCompoundOperation(opAsMap: JavaMap[String, _]): CompoundOperation = {
    val opMaps = opAsMap.get("ops").asInstanceOf[JavaList[JavaMap[String, Any]]]
    val ops = opMaps.asScala.toList.map(opMap => mapToDiscreteOperation(opMap))
    CompoundOperation(ops)
  }

  def mapToDiscreteOperation(opAsMap: JavaMap[String, _]): DiscreteOperation = {
    val t = opAsMap.get("type").asInstanceOf[String]
    t match {
      case "si" => mapToStringInsert(opAsMap)
      case "sr" => mapToStringRemove(opAsMap)
      case "ss" => mapToStringSet(opAsMap)

      case "ai" => mapToArrayInsert(opAsMap)
      case "ar" => mapToArrayRemove(opAsMap)
//      case "am" => mapToArrayMove(opAsMap)
//      case "ap" => mapToArrayReplace(opAsMap)
//      case "as" => mapToArraySet(opAsMap)
//
//      case "ot" => mapToObjectSet(opAsMap)
//      case "oa" => mapToObjectAdd(opAsMap)
//      case "or" => mapToObjectRemove(opAsMap)
//      case "os" => mapToObjectSet(opAsMap)
    }
  }
  
  def mapToStringInsert(map: JavaMap[String, _]): StringInsertOperation = {
    val path = map.get("path").asInstanceOf[JavaList[_]]
    val noOp = map.get("noOp").asInstanceOf[Boolean]
    val index = map.get("idx").asInstanceOf[Int]
    val value = map.get("val").asInstanceOf[String]
    StringInsertOperation(path.asScala.toList, noOp, index, value )
  }
  
  def mapToStringRemove(map: JavaMap[String, _]): StringRemoveOperation = {
    val path = map.get("path").asInstanceOf[JavaList[_]]
    val noOp = map.get("noOp").asInstanceOf[Boolean]
    val index = map.get("idx").asInstanceOf[Int]
    val value = map.get("val").asInstanceOf[String]
    StringRemoveOperation(path.asScala.toList, noOp, index, value )
  }
  
  def mapToStringSet(map: JavaMap[String, _]): StringSetOperation = {
    val path = map.get("path").asInstanceOf[JavaList[_]]
    val noOp = map.get("noOp").asInstanceOf[Boolean]
    val value = map.get("val").asInstanceOf[String]
    StringSetOperation(path.asScala.toList, noOp, value )
  }
  
  
  def mapToArrayInsert(map: JavaMap[String, _]): ArrayInsertOperation = {
    val path = map.get("path").asInstanceOf[JavaList[_]]
    val noOp = map.get("noOp").asInstanceOf[Boolean]
    val idx = map.get("idx").asInstanceOf[Int]
    val value = map.get("val")
    ArrayInsertOperation(path.asScala.toList, noOp, idx, javaToJValue(value))
  }
  
  def mapToArrayRemove(map: JavaMap[String, _]): ArrayRemoveOperation = {
    val path = map.get("path").asInstanceOf[JavaList[_]]
    val noOp = map.get("noOp").asInstanceOf[Boolean]
    val idx = map.get("idx").asInstanceOf[Int]
    ArrayRemoveOperation(path.asScala.toList, noOp, idx)
  }
  
//  def mapToArrayRemove(map: JavaMap[String, _]): ArrayRemoveOperation = {
//    val path = map.get("path").asInstanceOf[JavaList[_]]
//    val noOp = map.get("noOp").asInstanceOf[Boolean]
//    val idx = map.get("idx").asInstanceOf[Int]
//    ArrayRemoveOperation(path.asScala.toList, noOp, idx)
//  }
  
  
  def operationToMap(op: Operation): JavaMap[String, _] = {
    op match {
      case operation: CompoundOperation => compoundOperationToMap(operation)
      case operation: DiscreteOperation => discreteOperationToMap(operation)
    }
  }

  def compoundOperationToMap(op: CompoundOperation): JavaMap[String, Object] = {
    val ops = op.operations.map(discreteOp => operationToMap(discreteOp)).asJava
    val result = new HashMap[String, Object]()
    result.put("type", "compound")
    result.put("ops", ops)
    result
  }

  def discreteOperationToMap(op: DiscreteOperation): JavaMap[String, _] = {
    op match {
      
      // String Operations
      case StringInsertOperation(path, noOp, index, value) =>
        Map("type" -> "si", "path" -> path, "noOp" -> noOp, "idx" -> index, "val" -> value).asJava

      case StringRemoveOperation(path, noOp, index, value) =>
        Map("type" -> "sr", "path" -> path, "noOp" -> noOp, "idx" -> index, "val" -> value).asJava

      case StringSetOperation(path, noOp, value) =>
        Map("type" -> "ss", "path" -> path, "noOp" -> noOp, "val" -> value).asJava
        
      // Array Operations
      case ArrayInsertOperation(path, noOp, idx, value) =>
        Map("type" -> "ai", "path" -> path, "noOp" -> noOp, "idx" -> idx, "value" -> value).asJava

      case ArrayRemoveOperation(path, noOp, idx) =>
        Map("type" -> "ar", "path" -> path, "noOp" -> noOp, "idx" -> idx).asJava

      case ArrayMoveOperation(path, noOp, fromIdx, toIdx) =>
        Map("type" -> "am", "path" -> path, "noOp" -> noOp, "from" -> fromIdx, "to" -> toIdx).asJava

      case ArrayReplaceOperation(path, noOp, idx, value) =>
        Map("type" -> "ap", "path" -> path, "noOp" -> noOp, "idx" -> idx, "val" -> jValueToJava(value)).asJava

//      case ArraySetOperation(path, noOp, value) =>
//        Map("type" -> "as", "path" -> path, "noOp" -> noOp, "val" -> jValueToJava(value)).asJava

      // Object Operations
      case ObjectSetPropertyOperation(path, noOp, prop, value) =>
        Map("type" -> "ot", "path" -> path, "noOp" -> noOp, "val" -> jValueToJava(value)).asJava

      case ObjectAddPropertyOperation(path, noOp, prop, value) =>
        Map("type" -> "oa", "path" -> path, "noOp" -> noOp, "prop" -> prop, "val" -> jValueToJava(value)).asJava

      case ObjectRemovePropertyOperation(path, noOp, prop) =>
        Map("type" -> "or", "path" -> path, "noOp" -> noOp, "prop" -> prop).asJava

      case ObjectSetOperation(path, noOp, value) =>
        Map("type" -> "os", "path" -> path, "noOp" -> noOp, "val" -> jValueToJava(value)).asJava

      // Number Operations
      case NumberAddOperation(path, noOp, delta) =>
        Map("type" -> "na", "path" -> path, "noOp" -> noOp, "delta" -> delta).asJava

//      case NumberSetOperation(path, noOp, value) =>
//        Map("type" -> "ns", "path" -> path, "noOp" -> noOp, "val" -> jValueToJava(value)).asJava
    }
  }
  
  def jValueToJava(json: JValue): Any = {
    implicit val format = DefaultFormats
    json match {
      case JString(x) => x
      case JBool(x) => x
      case JNull => null
      case JDecimal(x) => x
      case JDouble(x) => x
      case JInt(x) => x
      case JNothing => null
      case obj: JObject => obj.extract[Map[String, _]].asJava
      case array: JArray => array.extract[List[_]].asJava
    }
  }
  
  def javaToJValue(value: Any): JValue = {
    implicit val format = DefaultFormats
    Extraction.decompose(value)
  }
}