package com.convergencelabs.server.domain.model.ot.xform

import org.scalatest.WordSpec
import com.convergencelabs.server.domain.model.ot.ops.ObjectAddPropertyOperation
import org.json4s.JsonAST.JObject
import org.scalatest.Matchers
import org.scalatest.Finders
import org.json4s.JsonAST.JString
import com.convergencelabs.server.domain.model.ot.ops.ObjectSetPropertyOperation
import com.convergencelabs.server.domain.model.ot.ops.ObjectSetOperation

class ObjectSetSetPropertyTFSpec extends WordSpec with Matchers {

  "A ObjectSetSetPropertyTF" when {

    "tranforming a set and a set property operation " must {
      "noOp the set property and not transform the set" in {
        val s = ObjectSetOperation(List(), false, JObject())
        val c = ObjectSetPropertyOperation(List(), false, "prop", JObject())
        
        val (s1, c1) = ObjectSetSetPropertyTF.transform(s, c)

        s1 shouldBe s
        c1 shouldBe ObjectSetPropertyOperation(List(), true, "prop", JObject())
      }
    }
  }
}