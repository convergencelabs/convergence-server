package com.convergencelabs.server.util

import java.math.BigInteger
import java.util.ArrayList
import java.util.HashMap
import java.util.{ List => JList }
import java.util.{ Map => JMap }

import scala.collection.JavaConverters.asScalaBufferConverter
import scala.collection.mutable.ListBuffer
import scala.math.BigInt.int2bigInt
import scala.math.BigInt.javaBigInteger2bigInt
import scala.math.BigInt.long2bigInt

import org.json4s.JArray
import org.json4s.JBool
import org.json4s.JDecimal
import org.json4s.JDouble
import org.json4s.JField
import org.json4s.JInt
import org.json4s.JNothing
import org.json4s.JNull
import org.json4s.JObject
import org.json4s.JString
import org.json4s.JValue
import org.json4s.JsonAST.JNumber

object JValueMapper {

  def jNumberToJava(jNumber: JNumber): Any = {
    jNumber match {
      case x: JDecimal => jValueToJava(x.asInstanceOf[JValue])
      case x: JDouble => jValueToJava(x.asInstanceOf[JValue])
      case x: JInt => jValueToJava(x.asInstanceOf[JValue])
    }
  }

  // scalastyle:off cyclomatic.complexity null
  def jValueToJava(jValue: JValue): Any = {
    jValue match {
      case JString(x) => x
      case JBool(x) => x
      case JNull => null
      case JDecimal(x) => x
      case JDouble(x) => x
      case JInt(bigInt) if bigInt.isValidInt => bigInt.intValue()
      case JInt(bigInt) if bigInt.isValidLong => bigInt.longValue()
      case JInt(bigInt) => bigInt.bigInteger
      case JNothing => null
      case obj: JObject => jObjectToMap(obj)
      case array: JArray => jArrayToList(array)
    }
  }
  // scalastyle:on cyclomatic.complexity null

  private[this] def jObjectToMap(obj: JObject): JMap[String, _] = {
    val result = new HashMap[String, Any]()
    obj.obj foreach {
      case (key, value) ⇒
        result.put(key, jValueToJava(value))
    }
    result
  }

  private[this] def jArrayToList(array: JArray): JList[_] = {
    val result = new ArrayList[Any]
    array.arr foreach { x ⇒ result.add(jValueToJava(x)) }
    result
  }

  // scalastyle:off cyclomatic.complexity null
  def javaToJValue(value: Any): JValue = {
    value match {
      case string: String => JString(string)
      case char: Char => JString(char.toString())

      case boolean: Boolean => JBool(boolean)
      case null => JNull

      case bigDecimal: BigDecimal => JDecimal(bigDecimal)
      case double: Double => JDouble(double)
      case float: Float => JDouble(float)

      case short: Short => JInt(short)
      case int: Int => JInt(int)
      case long: Long => JInt(long)
      case bigInterger: BigInteger => JInt(bigInterger)

      case map: JMap[_, _] => mapToJObject(map)
      case list: JList[_] => listToJArray(list)
      case x: Any => throw new IllegalArgumentException(s"Can not map object of class ${x.getClass.getName}")
    }
  }
  // scalastyle:on cyclomatic.complexity null

  private[this] def mapToJObject(map: JMap[_, _]): JObject = {
    val fields = ListBuffer[JField]()
    val entries = map.entrySet().iterator().asInstanceOf[java.util.Iterator[JMap.Entry[Any, Any]]]
    while (entries.hasNext()) {
      val entry = entries.next()
      entry.getKey match {
        case key: String => fields += JField(key, javaToJValue(entry.getValue))
        case _ => throw new IllegalArgumentException() // FIXME error message
      }
    }
    JObject(fields.toList)
  }

  private[this] def listToJArray(list: JList[_]): JArray = {
    val converted = list.asScala.toList.map { value => javaToJValue(value) }
    JArray(converted)
  }
}
