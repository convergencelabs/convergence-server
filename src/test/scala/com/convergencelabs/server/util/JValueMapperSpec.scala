package com.convergencelabs.server.util

import org.scalatest.WordSpec
import org.json4s._
import java.math.BigInteger

class JValueMapperSpec extends WordSpec {
  val complexJsonArray = JArray(List(
    JString("A String"),
    JInt(2),
    JBool(true),
    JNull,
    JDecimal(BigDecimal(5D)),
    JDouble(9D),
    JObject("key" -> JString("value"))))

  val complexJsonObject = JObject(
    "array" -> complexJsonArray,
    "int" -> JInt(4),
    "decimal" -> JDecimal(6D),
    "double" -> JDouble(10D),
    "string" -> JString("another string"),
    "null" -> JNull,
    "boolean" -> JBool(false),
    "object" -> JObject("something" -> JInt(2)))

  "An JValueMapper" when {
    "mapping simple types" must {
      "convert a JInt containing an int to an integer" in {
        val original = JInt(Int.MaxValue)
        val converted = JValueMapper.jValueToJava(original)
        assert(converted.getClass == classOf[java.lang.Integer])
        assert(converted == Int.MaxValue)
        assert(original == JValueMapper.javaToJValue(converted))
      }

      "convert a JInt containing a long to an Long" in {
        val original = JInt(Long.MaxValue)
        val converted = JValueMapper.jValueToJava(original)
        assert(converted.getClass == classOf[java.lang.Long])
        assert(converted == Long.MaxValue)
        assert(original == JValueMapper.javaToJValue(converted))
      }

      "convert a JInt containing a BigInteger to an BigInteger" in {
        val bigInteger = BigInteger.valueOf(Long.MaxValue)
        val value = bigInteger.add(bigInteger)
        val original = JInt(new BigInt(value))
        val converted = JValueMapper.jValueToJava(original)
        assert(converted == value)
        assert(original == JValueMapper.javaToJValue(converted))
      }

      "convert a JDouble containing a double to an Double" in {
        val original = JDouble(Double.MaxValue)
        val converted = JValueMapper.jValueToJava(original)
        assert(converted.getClass == classOf[java.lang.Double])
        assert(converted == Double.MaxValue)
        assert(original == JValueMapper.javaToJValue(converted))
      }

      "convert a JBool containing false to false" in {
        val value = false
        val original = JBool(value)
        val converted = JValueMapper.jValueToJava(original)
        assert(converted == value)
        assert(original == JValueMapper.javaToJValue(converted))
      }

      "convert a JBool containing true to true" in {
        val value = true
        val original = JBool(value)
        val converted = JValueMapper.jValueToJava(original)
        assert(converted == value)
        assert(original == JValueMapper.javaToJValue(converted))
      }

      "convert a JString to the correct string" in {
        val value = "string"
        val original = JString(value)
        val converted = JValueMapper.jValueToJava(original)
        assert(converted == value)
        assert(original == JValueMapper.javaToJValue(converted))
      }

      "convert a JNull to null" in {
        val original = JNull
        val converted = JValueMapper.jValueToJava(original)
        assert(converted == null)
        assert(original == JValueMapper.javaToJValue(converted))
      }

      "convert a JDecimal to a BigDecimal" in {
        val value = 2.3D
        val decimal = BigDecimal(value)
        val original = JDecimal(decimal)
        val converted = JValueMapper.jValueToJava(original)
        assert(converted == decimal)
        assert(original == JValueMapper.javaToJValue(converted))
      }
    }

    "mapping complext arrays and objects" must {
      "correctly map and unmap an array" in {
        val mapped = JValueMapper.jValueToJava(complexJsonArray)
        val unmapped = JValueMapper.javaToJValue(mapped)
        assert(complexJsonArray == unmapped)
      }

      "correctly map and unmap an object" in {
        val mapped = JValueMapper.jValueToJava(complexJsonObject)
        val unmapped = JValueMapper.javaToJValue(mapped)
        assert(complexJsonObject == unmapped)
      }
    }
  }
}