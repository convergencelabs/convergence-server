package com.convergencelabs.server.domain.model.ot

import org.scalatest.Finders
import org.scalatest.Matchers
import org.scalatest.WordSpec

// scalastyle:off multiple.string.literals
class ObjectRemovePropertyRemovePropertyTFSpec extends WordSpec with Matchers {

  val A = "A"
  val B = "B"

  "A ObjectRemovePropertyRemovePropertyTF" when {

    "tranforming a set and an add operation " must {

      /**
       *  O-RR-1
       */
      "not transform the operations if the properties are unequal" in {
        val s = ObjectRemovePropertyOperation(List(), false, A)
        val c = ObjectRemovePropertyOperation(List(), false, B)

        val (s1, c1) = ObjectRemovePropertyRemovePropertyTF.transform(s, c)

        s1 shouldBe s
        c1 shouldBe c
      }

      /**
       * O-RR-2
       */
      "noOp both operations if the properties are the same" in {
        val s = ObjectRemovePropertyOperation(List(), false, A)
        val c = ObjectRemovePropertyOperation(List(), false, A)

        val (s1, c1) = ObjectRemovePropertyRemovePropertyTF.transform(s, c)

        s1 shouldBe ObjectRemovePropertyOperation(List(), true, A)
        c1 shouldBe ObjectRemovePropertyOperation(List(), true, A)
      }
    }
  }
}
