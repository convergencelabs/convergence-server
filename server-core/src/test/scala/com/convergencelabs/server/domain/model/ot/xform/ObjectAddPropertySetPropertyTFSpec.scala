package com.convergencelabs.server.domain.model.ot

import org.json4s.JsonDSL.int2jvalue
import org.scalatest.Matchers
import org.scalatest.WordSpec

// scalastyle:off magic.number
class ObjectAddPropertySetPropertyTFSpec extends WordSpec with Matchers {

  val X = "X"
  val B = "B"

  "A ObjectAddPropertySetPropertyTF" when {

    "tranforming an add and a set operation " must {

      /**
       * O-AT-1
       */
      "do not transform the operations if the properties are unequal" in {
        val s = ObjectAddPropertyOperation(List(), false, X, 3)
        val c = ObjectSetPropertyOperation(List(), false, B, 4)

        val (s1, c1) = ObjectAddPropertySetPropertyTF.transform(s, c)

        s1 shouldBe s
        c1 shouldBe c
      }

      /**
       * O-AT-2
       */
      "throw an exception if the property names are equal" in {
        val s = ObjectAddPropertyOperation(List(), false, X, 3)
        val c = ObjectSetPropertyOperation(List(), false, X, 4)

        intercept[IllegalArgumentException] {
          val (s1, c1) = ObjectAddPropertySetPropertyTF.transform(s, c)
        }
      }
    }
  }
}
