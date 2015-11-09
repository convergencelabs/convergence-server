package com.convergencelabs.server.domain.model.ot.xform

import org.scalatest.WordSpec
import com.convergencelabs.server.domain.model.ot.ops.ObjectSetPropertyOperation
import org.json4s.JsonAST.JObject
import org.scalatest.Matchers
import org.scalatest.Finders
import org.json4s.JsonAST.JString
import com.convergencelabs.server.domain.model.ot.ops.ObjectRemovePropertyOperation
import com.convergencelabs.server.domain.model.ot.ops.ObjectAddPropertyOperation

class ObjectRemovePropertySetPropertyTFSpec extends WordSpec with Matchers {

  "A ObjectRemovePropertySetPropertyTF" when {

    "tranforming a remove and a set operation " must {
      "transform the set into an add, noOp the remove" in {
        val s = ObjectRemovePropertyOperation(List(), false, "prop")
        val c = ObjectSetPropertyOperation(List(), false, "prop", JObject())

        val (s1, c1) = ObjectRemovePropertySetPropertyTF.transform(s, c)
        
        s1 shouldBe ObjectRemovePropertyOperation(List(), true, "prop")
        c1 shouldBe ObjectAddPropertyOperation(List(), false, "prop", JObject())
      }
      
      "not transform the operations if the properties are unequal" in {
        val s = ObjectRemovePropertyOperation(List(), false, "prop1")
        val c = ObjectSetPropertyOperation(List(), false, "prop2", JObject())

        val (s1, c1) = ObjectRemovePropertySetPropertyTF.transform(s, c)
        
        s1 shouldBe s
        c1 shouldBe c
      }
    }
  }
}