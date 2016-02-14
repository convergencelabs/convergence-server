package com.convergencelabs.server.domain.model.ot

import org.json4s.JsonDSL.int2jvalue
import org.scalatest.Finders
import org.scalatest.Matchers
import org.scalatest.WordSpec

// scalastyle:off magic.number
class ObjectAddPropertyRemovePropertyTFSpec extends WordSpec with Matchers {

  val X = "X"
  val B = "B"

  "A ObjectAddPropertyRemovePropertyTF" when {

    "tranforming an add and a set operation " must {

      /**
       * O-AR-1
       */
      "do not transform the operations if the properties are unequal" in {
        val s = ObjectAddPropertyOperation(List(), false, X, 3)
        val c = ObjectRemovePropertyOperation(List(), false, B)

        val (s1, c1) = ObjectAddPropertyRemovePropertyTF.transform(s, c)

        s1 shouldBe s
        c1 shouldBe c
      }

      /**
       * O-AR-2
       */
      "throw an exception if the property names are equal" in {
        val s = ObjectAddPropertyOperation(List(), false, X, 3)
        val c = ObjectRemovePropertyOperation(List(), false, X)

        intercept[IllegalArgumentException] {
          val (s1, c1) = ObjectAddPropertyRemovePropertyTF.transform(s, c)
        }
      }
    }
  }
}
