package com.convergencelabs.server.frontend.realtime

import scala.annotation.implicitNotFound
import scala.language.postfixOps
import org.json4s.DefaultFormats
import org.json4s.Extraction
import org.json4s.JsonAST.JObject
import org.json4s.JsonDSL.jobject2assoc
import org.json4s.JsonDSL.pair2jvalue
import org.json4s.JsonDSL.string2jvalue
import org.json4s.JsonDSL.int2jvalue
import org.scalatest.Finders
import org.scalatest.Matchers
import org.scalatest.WordSpecLike
import org.scalatest.mockito.MockitoSugar
import org.json4s.JsonAST.JString
import org.json4s.reflect.Reflector
import org.json4s.`package`.MappingException
import org.json4s.JsonAST.JInt

class TypeMapSerializerSpec
    extends WordSpecLike
    with Matchers
    with MockitoSugar {

  "A TypeMapSerializer" when {
    "serializing a value" must {
      "serialize the type with the correct type field name and value for a type that has been mapped" in new TestFixture {
        val t1 = Test1("1")
        val default = Extraction.decompose(t1)(DefaultFormats).asInstanceOf[JObject]
        val expected = default ~ (typeField -> test1Type)
        val jValue = Extraction.decompose(t1)(format)
        jValue shouldBe expected
      }

      "throw an excpetion for a type that has not been mapped" in new TestFixture {
        val t3 = Test3(true)
        intercept[IllegalArgumentException] {
          Extraction.decompose(t3)(format)
        }
      }

      "throw an excpetion for a value that already contains a property that is the same as the typeField" in new TestFixture {
        val t4 = Test4("2", "3")
        intercept[IllegalArgumentException] {
          Extraction.decompose(t4)(format)
        }
      }
    }

    "deserializing a value" must {
      "deserialize a mapped class with a propper mapping" in new TestFixture {
        implicit val f = format
        val value = JObject(List((valueField, JString("5")), (typeField, JInt(test1Type))))
        val deserialized = Extraction.extract(value, Reflector.scalaTypeOf(classOf[TestData]))
        deserialized shouldBe Test1("5")
      }

      "throw an excpetion when deserializing a value with a type that is not registered" in new TestFixture {
        implicit val f = format
        val value = JObject(List((valueField, JString("6")), (typeField, JString("t3"))))
        intercept[IllegalArgumentException] {
          Extraction.extract(value, Reflector.scalaTypeOf(classOf[TestData]))
        }
      }

      "throw an excpetion when deserializing a value without the type field" in new TestFixture {
        implicit val f = format
        val value = JObject(List((valueField, JString("7"))))
        intercept[IllegalArgumentException] {
          Extraction.extract(value, Reflector.scalaTypeOf(classOf[TestData]))
        }
      }

      "throw an exception if the mapping fails" in new TestFixture {
        implicit val f = format
        val value = JObject(List(("wrong", JString("8")), (typeField, JInt(test1Type))))
        intercept[MappingException] {
          Extraction.extract(value, Reflector.scalaTypeOf(classOf[TestData]))
        }
      }
    }
  }

  trait TestFixture {
    val valueField = "value"
    val typeField = "t"
    val test1Type = 1
    val test2Type = 2
    val test4Type = 4

    val serializer = new TypeMapSerializer[TestData](typeField, Map(
      test1Type -> classOf[Test1],
      test2Type -> classOf[Test2],
      test4Type -> classOf[Test4]))

    implicit val format = DefaultFormats + serializer
  }

}

sealed trait TestData
case class Test1(value: String) extends TestData
case class Test2(value: Int) extends TestData
case class Test3(value: Boolean) extends TestData
case class Test4(value: String, t: String) extends TestData
