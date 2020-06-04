/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is part of the Convergence Server, which is released under
 * the terms of the GNU General Public License version 3 (GPLv3). A copy
 * of the GPLv3 should have been provided along with this file, typically
 * located in the "LICENSE" file, which is part of this source code package.
 * Alternatively, see <https://www.gnu.org/licenses/gpl-3.0.html> for the
 * full text of the GPLv3 license, if it was not provided.
 */

package com.convergencelabs.convergence.server.api.realtime

import org.json4s.JsonAST.{JInt, JObject, JString}
import org.json4s.JsonDSL.{int2jvalue, jobject2assoc, pair2jvalue}
import org.json4s.reflect.Reflector
import org.json4s.{DefaultFormats, Extraction, Formats, MappingException}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatestplus.mockito.MockitoSugar

import scala.language.postfixOps

class TypeMapSerializerSpec
    extends AnyWordSpecLike
    with Matchers
    with MockitoSugar {

  "A TypeMapSerializer" when {
    "serializing a value" must {
      "serialize the type with the correct type field name and value for a type that has been mapped" in new TestFixture {
        {
          val t1 = Test1("1")
          val default = Extraction.decompose(t1)(DefaultFormats).asInstanceOf[JObject]
          val expected = default ~ (typeField -> test1Type)
          val jValue = Extraction.decompose(t1)(format)
          jValue shouldBe expected
        }
      }

      "throw an exception for a type that has not been mapped" in new TestFixture {
        {
          val t3 = Test3(true)
          intercept[IllegalArgumentException] {
            Extraction.decompose(t3)(format)
          }
        }
      }

      "throw an exception for a value that already contains a property that is the same as the typeField" in new TestFixture {
        {
          val t4 = Test4("2", "3")
          intercept[IllegalArgumentException] {
            Extraction.decompose(t4)(format)
          }
        }
      }
    }

    "deserializing a value" must {
      "deserialize a mapped class with a proper mapping" in new TestFixture {
        {
          val value = JObject(List((valueField, JString("5")), (typeField, JInt(test1Type))))
          val deserialized = Extraction.extract(value, Reflector.scalaTypeOf(classOf[TestData]))
          deserialized shouldBe Test1("5")
        }
      }

      "throw an exception when deserializing a value with a type that is not registered" in new TestFixture {
        {
          val value = JObject(List((valueField, JString("6")), (typeField, JString("t3"))))
          intercept[IllegalArgumentException] {
            Extraction.extract(value, Reflector.scalaTypeOf(classOf[TestData]))
          }
        }
      }

      "throw an exception when deserializing a value without the type field" in new TestFixture {
        {
          val value = JObject(List((valueField, JString("7"))))
          intercept[IllegalArgumentException] {
            Extraction.extract(value, Reflector.scalaTypeOf(classOf[TestData]))
          }
        }
      }

      "throw an exception if the mapping fails" in new TestFixture {
        {
          val value = JObject(List(("wrong", JString("8")), (typeField, JInt(test1Type))))
          intercept[MappingException] {
            Extraction.extract(value, Reflector.scalaTypeOf(classOf[TestData]))
          }
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

    implicit val format: Formats = DefaultFormats + serializer
  }
}

sealed trait TestData
case class Test1(value: String) extends TestData
case class Test2(value: Int) extends TestData
case class Test3(value: Boolean) extends TestData
case class Test4(value: String, t: String) extends TestData
