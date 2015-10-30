package com.convergencelabs.server.util

import java.util.{ HashMap, ArrayList, Map => JMap, List => JList }
import org.json4s._
import scala.collection.mutable.ListBuffer
import scala.collection.JavaConversions._
import scala.collection.JavaConverters._
import java.math.BigInteger
import java.util.{ List => JList }
import java.util.{ Map => JMap }
import scala.math.BigInt.int2bigInt
import scala.math.BigInt.javaBigInteger2bigInt
import scala.math.BigInt.long2bigInt

object JValueMapper {

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
    array.arr foreach {
      case value ⇒
        result.add(jValueToJava(value))
    }
    result
  }

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
      case x => throw new IllegalArgumentException(s"Can not map object of class ${x.getClass.getName}") // FIXME error message
    }
  }

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