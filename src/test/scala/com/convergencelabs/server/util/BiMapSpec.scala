/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

package com.convergencelabs.server.util

import org.scalatest.Finders
import org.scalatest.Matchers
import org.scalatest.OptionValues.convertOptionToValuable
import org.scalatest.WordSpec

class BiMapSpec
    extends WordSpec
    with Matchers {

  val Key1 = "key1"
  val Key2 = "key2"

  val Value1 = "value1"
  val Value2 = "value2"

  "A BiMap" when {

    "being constructed" must {
      "detect tuples that violates the 1-to-1 mapping requirement" in {
        intercept[IllegalArgumentException] {
          BiMap(Key1 -> Value1, Key2 -> Value1)
        }
      }

      "detect a map that violates the 1-to-1 mapping requirement" in {
        intercept[IllegalArgumentException] {
          BiMap(Map(Key1 -> Value1, Key2 -> Value1))
        }
      }
    }

    "getting values" must {
      "must return the correct value for defined keys" in {
        val biMap = BiMap(Map(Key1 -> Value1, Key2 -> Value2))
        biMap.getValue(Key1).value shouldBe Value1
        biMap.getValue(Key2).value shouldBe Value2
      }

      "must return None for an undefined key" in {
        val biMap = BiMap(Map(Key1 -> Value1, Key2 -> Value2))
        biMap.getValue("key3") shouldBe None
      }
    }

    "getting keys" must {
      "must return the correct key for defined values" in {
        val biMap = BiMap(Map(Key1 -> Value1, Key2 -> Value2))
        biMap.getKey(Value1).value shouldBe Key1
        biMap.getKey(Value2).value shouldBe Key2
      }

      "must return None for an undefined value" in {
        val biMap = BiMap(Map(Key1 -> Value1, Key2 -> Value2))
        biMap.getKey("value3") shouldBe None
      }
    }

    "getting the keys" must {
      "must return the correct keys" in {
        val biMap = BiMap(Map(Key1 -> Value1, Key2 -> Value2))
        biMap.keys shouldBe Set(Key1, Key2)
      }
    }

    "getting the values" must {
      "must return the correct values" in {
        val biMap = BiMap(Map(Key1 -> Value1, Key2 -> Value2))
        biMap.values shouldBe Set(Value1, Value2)
      }
    }
  }
}
