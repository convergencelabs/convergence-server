package com.convergencelabs.server.domain.model.ot

import org.json4s.JsonAST.JObject
import org.json4s.JsonAST.JString
import org.json4s.JsonDSL.int2jvalue
import org.scalatest.Finders
import org.scalatest.Matchers
import org.scalatest.WordSpec
import org.json4s.JsonAST.JInt

// scalastyle:off magic.number
class ObjectSetSetTFSpec extends WordSpec with Matchers {
  val X = "X"
  val Y = "Y"

  "A ObjectSetSetTF" when {

    "tranforming a set and a set  operation " must {

      /**
       * O-SS-1
       */
      "noOp the client's set if the values are not equal" in {
        val s = ObjectSetOperation(List(), false, JObject(X -> JInt(3)))
        val c = ObjectSetOperation(List(), false, JObject(Y -> JInt(4)))

        val (s1, c1) = ObjectSetSetTF.transform(s, c)

        s1 shouldBe s
        c1 shouldBe ObjectSetOperation(List(), true, JObject(Y -> JInt(4)))
      }

      /**
       * O-SS-2
       */
      "noOp both operations if the properties and values are equal" in {
        val s = ObjectSetOperation(List(), false, JObject(X -> JInt(3)))
        val c = ObjectSetOperation(List(), false, JObject(X -> JInt(3)))

        val (s1, c1) = ObjectSetSetTF.transform(s, c)

        s1 shouldBe ObjectSetOperation(List(), true, JObject(X -> JInt(3)))
        c1 shouldBe ObjectSetOperation(List(), true, JObject(X -> JInt(3)))
      }
    }
  }
}
