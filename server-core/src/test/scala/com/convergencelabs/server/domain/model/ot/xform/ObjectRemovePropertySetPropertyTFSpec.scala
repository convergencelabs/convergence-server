package com.convergencelabs.server.domain.model.ot

import org.json4s.JsonDSL.int2jvalue
import org.scalatest.Finders
import org.scalatest.Matchers
import org.scalatest.WordSpec

// scalastyle:off multiple.string.literals magic.number
class ObjectRemovePropertySetPropertyTFSpec extends WordSpec with Matchers {
  val A = "A"
  val B = "B"

  "A ObjectRemovePropertySetPropertyTF" when {

    "tranforming a remove and a set operation " must {

      /**
       * O-RT-1
       */
      "not transform the operations if the properties are unequal" in {
        val s = ObjectRemovePropertyOperation(List(), false, B)
        val c = ObjectSetPropertyOperation(List(), false, A, 4)

        val (s1, c1) = ObjectRemovePropertySetPropertyTF.transform(s, c)

        s1 shouldBe s
        c1 shouldBe c
      }

      /**
       * O-RT-2
       */
      "transform the set into an add, noOp the remove" in {
        val s = ObjectRemovePropertyOperation(List(), false, B)
        val c = ObjectSetPropertyOperation(List(), false, B, 4)

        val (s1, c1) = ObjectRemovePropertySetPropertyTF.transform(s, c)

        s1 shouldBe ObjectRemovePropertyOperation(List(), true, B)
        c1 shouldBe ObjectAddPropertyOperation(List(), false, B, 4)
      }
    }
  }
}
