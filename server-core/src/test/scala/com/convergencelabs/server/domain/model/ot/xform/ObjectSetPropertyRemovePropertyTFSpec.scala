package com.convergencelabs.server.domain.model.ot

import org.json4s.JsonDSL.int2jvalue
import org.scalatest.Finders
import org.scalatest.Matchers
import org.scalatest.WordSpec

// scalastyle:off multiple.string.literals magic.number
class ObjectSetPropertyRemovePropertyTFSpec extends WordSpec with Matchers {

  val A = "A"
  val B = "B"

  "A ObjectSetPropertyRemovePropertyTF" when {

    "tranforming a set and a set operation " must {

      /**
       * O-TR-1
       */
      "do not transform the operations if the properties are unequal" in {
        val s = ObjectSetPropertyOperation(List(), false, A, 4)
        val c = ObjectRemovePropertyOperation(List(), false, B)

        val (s1, c1) = ObjectSetPropertyRemovePropertyTF.transform(s, c)

        s1 shouldBe s
        c1 shouldBe c
      }

      /**
       * O-TR-2
       */
      "transform a set into an add if the set and the remove are the same property, noOp the remove" in {
        val s = ObjectSetPropertyOperation(List(), false, B, 4)
        val c = ObjectRemovePropertyOperation(List(), false, B)

        val (s1, c1) = ObjectSetPropertyRemovePropertyTF.transform(s, c)

        s1 shouldBe ObjectAddPropertyOperation(List(), false, B, 4)
        c1 shouldBe ObjectRemovePropertyOperation(List(), true, B)
      }

    }
  }
}
