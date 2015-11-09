package com.convergencelabs.server.domain.model.ot.xform

import org.scalatest.WordSpec
import com.convergencelabs.server.domain.model.ot.ops.NumberAddOperation
import org.json4s.JsonAST._
import org.scalatest.Matchers
import org.scalatest.Finders
import com.convergencelabs.server.domain.model.ot.ops.ObjectSetPropertyOperation
import com.convergencelabs.server.domain.model.ot.ops.NumberSetOperation

class NumberAddSetTFSpec extends WordSpec with Matchers {

  "A NumberAddSetTF" when {

    "tranforming an add and an set operation " must {
      "noOp the server's add operation and not transform the client's set operation" in {
        val s = NumberAddOperation(List(), false, JInt(3))
        val c = NumberSetOperation(List(), false, JDouble(3D))
        
        val (s1, c1) = NumberAddSetTF.transform(s, c)
        
        s1 shouldBe NumberAddOperation(List(), true, JInt(3))
        c1 shouldBe c
      }
    }
  }
}