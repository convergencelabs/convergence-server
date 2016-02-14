package com.convergencelabs.server.domain.model.ot

import org.json4s.JsonAST.JObject
import org.json4s.JsonDSL.int2jvalue
import org.scalatest.Finders
import org.scalatest.Matchers
import org.scalatest.WordSpec

// scalastyle:off magic.number
class ObjectSetPropertySetPropertyTFSpec extends WordSpec with Matchers {

  val A = "A"
  val B = "B"

  "A ObjectSetPropertySetPropertyTF" when {

    "tranforming a set and a set operation " must {

      /**
       * O-TT-1
       */
      "do not transform the operations if the properties are unequal" in {
        val s = ObjectSetPropertyOperation(List(), false, A, 3)
        val c = ObjectSetPropertyOperation(List(), false, B, 4)

        val (s1, c1) = ObjectSetPropertySetPropertyTF.transform(s, c)

        s1 shouldBe s
        c1 shouldBe c
      }

      /**
       * O-TT-2
       */
      "noOp the client's operation if the properties are the same and the values are unequal" in {
        val s = ObjectSetPropertyOperation(List(), false, A, 3)
        val c = ObjectSetPropertyOperation(List(), false, A, 4)

        val (s1, c1) = ObjectSetPropertySetPropertyTF.transform(s, c)

        s1 shouldBe s
        c1 shouldBe ObjectSetPropertyOperation(List(), true, A, 4)
      }

      /**
       * O-TT-3
       */
      "noOp both operations if the properties and values are equal" in {
        val s = ObjectSetPropertyOperation(List(), false, A, 3)
        val c = ObjectSetPropertyOperation(List(), false, A, 3)

        val (s1, c1) = ObjectSetPropertySetPropertyTF.transform(s, c)

        s1 shouldBe ObjectSetPropertyOperation(List(), true, A, 3)
        c1 shouldBe ObjectSetPropertyOperation(List(), true, A, 3)
      }
    }
  }
}
