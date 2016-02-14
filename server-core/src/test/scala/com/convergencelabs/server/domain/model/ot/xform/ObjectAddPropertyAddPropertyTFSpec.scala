package com.convergencelabs.server.domain.model.ot

import org.json4s.JsonAST.JObject
import org.json4s.JsonAST.JString
import org.json4s.JsonDSL.int2jvalue
import org.scalatest.Finders
import org.scalatest.Matchers
import org.scalatest.WordSpec

// scalastyle:off magic.number multiple.string.literals
class ObjectAddPropertyAddPropertyTFSpec extends WordSpec with Matchers {

  val X = "X"
  val Y = "Y"

  "A ObjectAddPropertyAddPropertyTF" when {

    "tranforming an add and add operation " must {

      /**
       * O-AA-1
       */
      "not transform either operation if they are setting different properties" in {
        val s = ObjectAddPropertyOperation(List(), false, X, 3)
        val c = ObjectAddPropertyOperation(List(), false, Y, 4)

        val (s1, c1) = ObjectAddPropertyAddPropertyTF.transform(s, c)

        s1 shouldBe s
        c1 shouldBe c
      }

      /**
       * O-AA-2
       */
      "convert the server's add into a set and noOp the client if the ops add the same prop to different values" in {
        val s = ObjectAddPropertyOperation(List(), false, X, 3)
        val c = ObjectAddPropertyOperation(List(), false, X, 4)

        val (s1, c1) = ObjectAddPropertyAddPropertyTF.transform(s, c)

        s1 shouldBe ObjectSetPropertyOperation(List(), false, X, 3)
        c1 shouldBe ObjectAddPropertyOperation(List(), true, X, 4)
      }

      /**
       * O-AA-3
       */
      "noOp both operations if they are adding the same property with the same value" in {
        val s = ObjectAddPropertyOperation(List(), false, X, 3)
        val c = ObjectAddPropertyOperation(List(), false, X, 3)

        val (s1, c1) = ObjectAddPropertyAddPropertyTF.transform(s, c)

        s1 shouldBe ObjectAddPropertyOperation(List(), true, X, 3)
        c1 shouldBe ObjectAddPropertyOperation(List(), true, X, 3)
      }
    }
  }
}
