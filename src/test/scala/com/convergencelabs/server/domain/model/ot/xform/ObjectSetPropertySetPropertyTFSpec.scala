package com.convergencelabs.server.domain.model.ot.xform

import org.scalatest.WordSpec
import com.convergencelabs.server.domain.model.ot.ops.ObjectAddPropertyOperation
import org.json4s.JsonAST.JObject
import org.scalatest.Matchers
import org.scalatest.Finders
import org.json4s.JsonAST.JString
import com.convergencelabs.server.domain.model.ot.ops.ObjectSetPropertyOperation
import org.json4s.JsonAST.JInt

class ObjectSetPropertySetPropertyTFSpec extends WordSpec with Matchers {

  "A ObjectSetPropertySetPropertyTF" when {

    "tranforming a set and a set operation " must {
      "do not transform the operations if the properties are unequal" in {
        val s = ObjectSetPropertyOperation(List(), false, "prop1", JObject())
        val c = ObjectSetPropertyOperation(List(), false, "prop2", JObject())

        val (s1, c1) = ObjectSetPropertySetPropertyTF.transform(s, c)
        
        s1 shouldBe s
        c1 shouldBe c
      }
      
      "noOp the client's operation if the properties are the same and the values are unequal" in {
        val s = ObjectSetPropertyOperation(List(), false, "prop", JInt(1))
        val c = ObjectSetPropertyOperation(List(), false, "prop", JObject())

        val (s1, c1) = ObjectSetPropertySetPropertyTF.transform(s, c)
        
        s1 shouldBe s
        c1 shouldBe ObjectSetPropertyOperation(List(), true, "prop", JObject())
      }
      
      "noOp both operations if the properties and values are equal" in {
        val s = ObjectSetPropertyOperation(List(), false, "prop", JInt(1))
        val c = ObjectSetPropertyOperation(List(), false, "prop", JInt(1))

        val (s1, c1) = ObjectSetPropertySetPropertyTF.transform(s, c)
        
        s1 shouldBe ObjectSetPropertyOperation(List(), true, "prop", JInt(1))
        c1 shouldBe ObjectSetPropertyOperation(List(), true, "prop", JInt(1))
      }
    }
  }
}