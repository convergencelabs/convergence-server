package com.convergencelabs.server.domain.model.ot

import org.json4s.JsonDSL.int2jvalue
import org.scalatest.Matchers
import org.scalatest.WordSpec

// scalastyle:off magic.number
class ObjectRemovePropertyAddPropertyTFSpec extends WordSpec with Matchers {

  val X = "X"
  val B = "B"

  "A ObjectRemovePropertyAddPropertyTF" when {

    "tranforming a set and an add operation " must {

      /**
       * O-RA-1
       */
      "do not transform the operations if the properties are unequal" in {
        val s = ObjectRemovePropertyOperation(List(), false, B)
        val c = ObjectAddPropertyOperation(List(), false, X, 3)

        val (s1, c1) = ObjectRemovePropertyAddPropertyTF.transform(s, c)

        s1 shouldBe s
        c1 shouldBe c
      }

      /**
       * O-RA-2
       */
      "throw an exception if the property names are equal" in {
        val s = ObjectRemovePropertyOperation(List(), false, X)
        val c = ObjectAddPropertyOperation(List(), false, X, 3)

        intercept[IllegalArgumentException] {
          val (s1, c1) = ObjectRemovePropertyAddPropertyTF.transform(s, c)
        }
      }
    }
  }
}
