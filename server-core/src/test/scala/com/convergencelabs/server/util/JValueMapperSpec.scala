package com.convergencelabs.server.util

import java.math.BigInteger

import scala.BigDecimal
import scala.math.BigDecimal.double2bigDecimal
import scala.math.BigInt.int2bigInt
import scala.math.BigInt.long2bigInt

import org.json4s.JArray
import org.json4s.JBool
import org.json4s.JDecimal
import org.json4s.JDouble
import org.json4s.JInt
import org.json4s.JNothing
import org.json4s.JNull
import org.json4s.JObject
import org.json4s.JString
import org.json4s.JsonAST.JNumber
import org.scalatest.Finders
import org.scalatest.Matchers
import org.scalatest.WordSpec

// scalastyle:off null
class JValueMapperSpec
    extends WordSpec
    with Matchers {
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
        converted shouldBe a[java.lang.Integer]
        converted shouldBe Int.MaxValue
        JValueMapper.javaToJValue(converted) shouldBe original
      }

      "convert a JInt containing a long to a Long" in {
        val original = JInt(Long.MaxValue)
        val converted = JValueMapper.jValueToJava(original)
        converted shouldBe a[java.lang.Long]
        converted shouldBe Long.MaxValue
        JValueMapper.javaToJValue(converted) shouldBe original
      }

      "convert a JInt containing a BigInteger to an BigInteger" in {
        val bigInteger = BigInteger.valueOf(Long.MaxValue)
        val value = bigInteger.add(bigInteger)
        val original = JInt(new BigInt(value))
        val converted = JValueMapper.jValueToJava(original)
        converted shouldBe value
        JValueMapper.javaToJValue(converted) shouldBe original
      }

      "convert a JInt (JNumber) containing a long to a Long" in {
        val original: JNumber = JInt(Long.MaxValue)
        val converted = JValueMapper.jNumberToJava(original)
        converted shouldBe a[java.lang.Long]
        converted shouldBe Long.MaxValue
        JValueMapper.javaToJValue(converted) shouldBe original
      }

      "convert a JDouble containing a double to an Double" in {
        val original = JDouble(Double.MaxValue)
        val converted = JValueMapper.jValueToJava(original)
        converted shouldBe a[java.lang.Double]
        converted shouldBe Double.MaxValue
        JValueMapper.javaToJValue(converted) shouldBe original
      }

      "convert a JDouble (JNumber) containing a double to an Double" in {
        val original: JNumber = JDouble(Double.MaxValue)
        val converted = JValueMapper.jNumberToJava(original)
        converted shouldBe a[java.lang.Double]
        converted shouldBe Double.MaxValue
        JValueMapper.javaToJValue(converted) shouldBe original
      }

      "convert a JBool containing false to false" in {
        val value = false
        val original = JBool(value)
        val converted = JValueMapper.jValueToJava(original)
        converted shouldBe value
        JValueMapper.javaToJValue(converted) shouldBe original
      }

      "convert a JBool containing true to true" in {
        val value = true
        val original = JBool(value)
        val converted = JValueMapper.jValueToJava(original)
        converted shouldBe value
        JValueMapper.javaToJValue(converted) shouldBe original
      }

      "convert a JString to the correct string" in {
        val value = "string"
        val original = JString(value)
        val converted = JValueMapper.jValueToJava(original)
        converted shouldBe value
        JValueMapper.javaToJValue(converted) shouldBe original
      }

      "convert a JNull to null" in {
        val original = JNull
        val converted = JValueMapper.jValueToJava(original)
        assert(converted == null)
        JValueMapper.javaToJValue(converted) shouldBe original
      }

      "convert a JNothing to null" in {
        val original = JNothing
        val converted = JValueMapper.jValueToJava(original)
        assert(converted == null)
      }

      "convert a JDecimal to a BigDecimal" in {
        val value = 2.3D
        val decimal = BigDecimal(value)
        val original = JDecimal(decimal)
        val converted = JValueMapper.jValueToJava(original)
        converted shouldBe decimal
        JValueMapper.javaToJValue(converted) shouldBe original
      }

      "convert a JDecimal (JNumber) to a BigDecimal" in {
        val value = 2.3D
        val decimal = BigDecimal(value)
        val original: JNumber = JDecimal(decimal)
        val converted = JValueMapper.jNumberToJava(original)
        converted shouldBe decimal
        JValueMapper.javaToJValue(converted) shouldBe original
      }

      "convert a short to a JInt" in {
        val short: Short = 5
        val converted = JValueMapper.javaToJValue(short)
        converted shouldBe a[JInt]
        converted.values shouldBe short
      }

      "convert a Float to a JInt" in {
        val float = 5.0f
        val converted = JValueMapper.javaToJValue(float)
        converted shouldBe a[JDouble]
        converted.values shouldBe float
      }

      "convert a Char to a JString" in {
        val char = 'c'
        val converted = JValueMapper.javaToJValue(char)
        converted shouldBe a[JString]
        converted.values shouldBe char.toString()
      }
    }

    "mapping complext arrays and objects" must {
      "correctly map and unmap an array" in {
        val mapped = JValueMapper.jValueToJava(complexJsonArray)
        val unmapped = JValueMapper.javaToJValue(mapped)
        unmapped shouldBe complexJsonArray
      }

      "correctly map and unmap an object" in {
        val mapped = JValueMapper.jValueToJava(complexJsonObject)
        val unmapped = JValueMapper.javaToJValue(mapped)
        unmapped shouldBe complexJsonObject
      }
    }

    "mapping an invalid java objects" must {
      "throw an excpetion for an unsupported type" in {
        intercept[IllegalArgumentException] {
          JValueMapper.javaToJValue(new Object())
        }
      }

      "throw an excpetion for a map with non-string keys" in {
        val map = new java.util.HashMap[Object, Object]()
        map.put(new Object(), "test")
        intercept[IllegalArgumentException] {
          JValueMapper.javaToJValue(map)
        }
      }
    }
  }
}
