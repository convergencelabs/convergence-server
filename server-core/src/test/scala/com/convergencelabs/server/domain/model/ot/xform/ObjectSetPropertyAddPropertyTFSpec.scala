package com.convergencelabs.server.domain.model.ot

import org.json4s.JsonDSL.int2jvalue
import org.scalatest.Finders
import org.scalatest.Matchers
import org.scalatest.WordSpec

// scalastyle:off magic.number
class ObjectSetPropertyAddPropertyTFSpec extends WordSpec with Matchers {
  val A = "A"
  val B = "B"

  "A ObjectSetPropertyAddPropertyTF" when {

    "tranforming a set and an add operation " must {

      /**
       * O-TA-1
       */
      "do not transform the operations if the properties are unequal" in {
        val s = ObjectSetPropertyOperation(List(), false, A, 3)
        val c = ObjectAddPropertyOperation(List(), false, B, 4)

        val (s1, c1) = ObjectSetPropertyAddPropertyTF.transform(s, c)

        s1 shouldBe s
        c1 shouldBe c
      }

      /**
       * O-TA-2
       */
      "throw an exception if the property names are equal" in {
        val s = ObjectSetPropertyOperation(List(), false, A, 3)
        val c = ObjectAddPropertyOperation(List(), false, A, 4)

        intercept[IllegalArgumentException] {
          val (s1, c1) = ObjectSetPropertyAddPropertyTF.transform(s, c)
        }
      }
    }
  }
}
