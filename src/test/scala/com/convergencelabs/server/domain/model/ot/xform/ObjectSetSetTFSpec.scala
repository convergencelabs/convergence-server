package com.convergencelabs.server.domain.model.ot.xform

import org.scalatest.WordSpec
import com.convergencelabs.server.domain.model.ot.ops.ObjectAddPropertyOperation
import org.json4s.JsonAST.JObject
import org.scalatest.Matchers
import org.scalatest.Finders
import org.json4s.JsonAST.JString
import com.convergencelabs.server.domain.model.ot.ops.ObjectSetPropertyOperation
import com.convergencelabs.server.domain.model.ot.ops.ObjectSetOperation

class ObjectSetSetTFSpec extends WordSpec with Matchers {

  "A ObjectSetSetTF" when {

    "tranforming a set and a set  operation " must {
      "noOp the client's set if the values are not equal" in {
        val s = ObjectSetOperation(List(), false, JObject("foo" -> JString("bar")))
        val c = ObjectSetOperation(List(), false, JObject("foo" -> JString("baz")))
        
        val (s1, c1) = ObjectSetSetTF.transform(s, c)

        s1 shouldBe ObjectSetOperation(List(), false, JObject("foo" -> JString("bar")))
        c1 shouldBe ObjectSetOperation(List(), true, JObject("foo" -> JString("baz")))
      }
      
      "noOp both operations if the properties and values are equal" in {
        val s = ObjectSetOperation(List(), false, JObject("foo" -> JString("bar")))
        val c = ObjectSetOperation(List(), false, JObject("foo" -> JString("bar")))
        
        val (s1, c1) = ObjectSetSetTF.transform(s, c)

        s1 shouldBe ObjectSetOperation(List(), true, JObject("foo" -> JString("bar")))
        c1 shouldBe ObjectSetOperation(List(), true, JObject("foo" -> JString("bar")))
      }
    }
  }
}