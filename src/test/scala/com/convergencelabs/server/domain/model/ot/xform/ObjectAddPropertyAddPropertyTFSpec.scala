package com.convergencelabs.server.domain.model.ot.xform

import org.scalatest.WordSpec
import com.convergencelabs.server.domain.model.ot.ops.ObjectAddPropertyOperation
import org.json4s.JsonAST.JObject
import org.scalatest.Matchers
import org.scalatest.Finders
import org.json4s.JsonAST.JString
import com.convergencelabs.server.domain.model.ot.ops.ObjectSetPropertyOperation

class ObjectAddPropertyAddPropertyTFSpec extends WordSpec with Matchers {

  "A ObjectAddPropertyAddPropertyTF" when {

    "tranforming an add and add operation " must {
      "noOp both operations if they are adding the same property with the same value" in {
        val s = ObjectAddPropertyOperation(List(), false, "prop", JObject())
        val c = ObjectAddPropertyOperation(List(), false, "prop", JObject())
        
        val (s1, c1) = ObjectAddPropertyAddPropertyTF.transform(s, c)
        
        s1 shouldBe ObjectAddPropertyOperation(List(), true, "prop", JObject())
        c1 shouldBe ObjectAddPropertyOperation(List(), true, "prop", JObject())
      }
      
      "not transform either operation if they are setting different properties" in {
        val s = ObjectAddPropertyOperation(List(), false, "prop1", JObject())
        val c = ObjectAddPropertyOperation(List(), false, "prop2", JObject())
        
        val (s1, c1) = ObjectAddPropertyAddPropertyTF.transform(s, c)
        
        s1 shouldBe s
        c1 shouldBe c
      }
      
      "convert the server's add into a set and noOp the client if the ops add the same prop to different values" in {
        val s = ObjectAddPropertyOperation(List(), false, "prop", JObject())
        val c = ObjectAddPropertyOperation(List(), false, "prop", JString(""))
        
        val (s1, c1) = ObjectAddPropertyAddPropertyTF.transform(s, c)
        
        s1 shouldBe ObjectSetPropertyOperation(List(), false, "prop", JObject())
        c1 shouldBe ObjectAddPropertyOperation(List(), true, "prop", JString(""))
      }
    }
  }
}