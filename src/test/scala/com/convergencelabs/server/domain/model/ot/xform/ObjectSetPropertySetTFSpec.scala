package com.convergencelabs.server.domain.model.ot.xform

import org.scalatest.WordSpec
import com.convergencelabs.server.domain.model.ot.ops.ObjectAddPropertyOperation
import org.json4s.JsonAST.JObject
import org.scalatest.Matchers
import org.scalatest.Finders
import org.json4s.JsonAST.JString
import com.convergencelabs.server.domain.model.ot.ops.ObjectSetPropertyOperation
import com.convergencelabs.server.domain.model.ot.ops.ObjectSetOperation

class ObjectSetPropertySetTFSpec extends WordSpec with Matchers {

  "A ObjectSetPropertySetTF" when {

    "tranforming an set property and a set operation " must {
      "throw an exception if the property names are equal" in {
        val s = ObjectSetPropertyOperation(List(), false, "prop", JObject())
        val c = ObjectSetOperation(List(), false, JObject())
        
        val (s1, c1) = ObjectSetPropertySetTF.transform(s, c)

        s1 shouldBe ObjectSetPropertyOperation(List(), true, "prop", JObject())
        c1 shouldBe c
      }
    }
  }
}