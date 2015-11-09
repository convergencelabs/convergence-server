package com.convergencelabs.server.domain.model.ot.xform

import org.json4s.JsonAST.JString
import org.scalatest.Finders
import org.scalatest.WordSpec
import com.convergencelabs.server.domain.model.ot.ops.StringSetOperation

class StringSetSetTFSpec extends WordSpec {

  val Path = List(1, 2)

  "A StringSetSetTF" when {

    "tranforming a server set against a client set " must {
      
      "noOp the client's set operation and not transform the server's set if the sets have different values" in {
        val s = StringSetOperation(Path, false, "y")
        val c = StringSetOperation(Path, false, "x")
        
        val (s1, c1) = StringSetSetTF.transform(s, c)

        assert(s1 == s)
        assert(c1 == StringSetOperation(Path, true, "x"))
      }
      
      "noOp the client's set operation and not transform the server's set if the sets have the same values" in {
        val s = StringSetOperation(Path, false, "x")
        val c = StringSetOperation(Path, false, "x")
        
        val (s1, c1) = StringSetSetTF.transform(s, c)

        assert(s1 == StringSetOperation(Path, true, "x"))
        assert(c1 == StringSetOperation(Path, true, "x"))
      }
    }
  }
}