package com.convergencelabs.server.domain.model.ot.xform

import org.json4s.JsonAST.JString
import org.json4s.JsonAST.JString
import org.scalatest.Finders
import org.scalatest.WordSpec

import com.convergencelabs.server.domain.model.ot.ops.StringInsertOperation
import com.convergencelabs.server.domain.model.ot.ops.StringSetOperation

class StringInsertSetTFSpec extends WordSpec {

  val Path = List(1, 2)
  val ClientVal = "x"
  val ServerVal = "y"

  "A StringInsertSetTF" when {

    "tranforming a server insert against a client remove " must {
      
      "noOp the server's operation and not transform the client's" in {
        val s = StringInsertOperation(Path, false, 2, ServerVal)
        val c = StringSetOperation(Path, false, ClientVal)
        
        val (s1, c1) = StringInsertSetTF.transform(s, c)

        assert(s1 == StringInsertOperation(Path, true, 2, ServerVal))
        assert(c1 == c)
      }
    }
  }
}