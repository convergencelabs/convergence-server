package com.convergencelabs.server.domain.model.ot

import org.scalatest.WordSpec
import org.json4s.JsonAST.JObject
import org.scalatest.Matchers
import org.scalatest.Finders
import org.json4s.JsonAST.JString

class ObjectAddPropertySetTFSpec extends WordSpec with Matchers {

  val X = "X"

  "A ObjectAddPropertySetTF" when {

    "tranforming an add and a set operation " must {

      /**
       * O-AS-1
       */
      "noOp the add property" in {
        val s = ObjectAddPropertyOperation(List(), false, X, JObject())
        val c = ObjectSetOperation(List(), false, JObject())

        val (s1, c1) = ObjectAddPropertySetTF.transform(s, c)

        s1 shouldBe ObjectAddPropertyOperation(List(), true, X, JObject())
        c1 shouldBe c
      }
    }
  }
}
