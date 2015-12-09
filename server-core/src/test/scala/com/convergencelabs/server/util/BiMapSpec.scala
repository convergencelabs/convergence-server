package com.convergencelabs.server.util

import org.scalatest.Finders
import org.scalatest.Matchers
import org.scalatest.OptionValues.convertOptionToValuable
import org.scalatest.WordSpec

class BiMapSpec
    extends WordSpec
    with Matchers {

  "A BiMap" when {

    "being constructed" must {
      "detect tuples that violates the 1-to-1 mapping requirement" in {
        intercept[IllegalArgumentException] {
          BiMap("key1" -> "value1", "key2" -> "value1")
        }
      }

      "detect a map that violates the 1-to-1 mapping requirement" in {
        intercept[IllegalArgumentException] {
          BiMap(Map("key1" -> "value1", "key2" -> "value1"))
        }
      }
    }

    "getting values" must {
      "must return the correct value for defined keys" in {
        val biMap = BiMap(Map("key1" -> "value1", "key2" -> "value2"))
        biMap.getValue("key1").value shouldBe "value1"
        biMap.getValue("key2").value shouldBe "value2"
      }

      "must return None for an undefined key" in {
        val biMap = BiMap(Map("key1" -> "value1", "key2" -> "value2"))
        biMap.getValue("key3") shouldBe None
      }
    }

    "getting keys" must {
      "must return the correct key for defined values" in {
        val biMap = BiMap(Map("key1" -> "value1", "key2" -> "value2"))
        biMap.getKey("value1").value shouldBe "key1"
        biMap.getKey("value2").value shouldBe "key2"
      }

      "must return None for an undefined value" in {
        val biMap = BiMap(Map("key1" -> "value1", "key2" -> "value2"))
        biMap.getKey("value3") shouldBe None
      }
    }

    "getting the keys" must {
      "must return the correct keys" in {
        val biMap = BiMap(Map("key1" -> "value1", "key2" -> "value2"))
        biMap.keys shouldBe Set("key1", "key2")
      }
    }

    "getting the values" must {
      "must return the correct values" in {
        val biMap = BiMap(Map("key1" -> "value1", "key2" -> "value2"))
        biMap.values shouldBe Set("value1", "value2")
      }
    }
  }
}
