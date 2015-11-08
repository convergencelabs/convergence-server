package com.convergencelabs.server.domain.model.ot.xform

import org.json4s.JsonAST.JString
import org.scalatest.Finders
import org.scalatest.WordSpec

import com.convergencelabs.server.domain.model.ot.ops.ArrayInsertOperation
import com.convergencelabs.server.domain.model.ot.ops.ArrayRemoveOperation

class ArrayInsertRemoveTFSpec extends WordSpec {

  val Path = List(1, 2)
  val ClientVal = JString("x")
  val ServerVal = JString("y")

  "A ArrayInsertRemoveTF" when {

    "tranforming a server insert against a client remove " must {
      
      "increment the client index if both operations target the same index" in {
        val s = ArrayInsertOperation(Path, false, 2, ServerVal)
        val c = ArrayRemoveOperation(Path, false, 2)
        
        val (s1, c1) = ArrayInsertRemoveTF.transform(s, c)

        assert(s1 == s)
        assert(c1 == ArrayRemoveOperation(Path, false, 3))
      }
      
      "increment the client index if the server's index is before the client's" in {
        val s = ArrayInsertOperation(Path, false, 2, ServerVal)
        val c = ArrayRemoveOperation(Path, false, 3)
        
        val (s1, c1) = ArrayInsertRemoveTF.transform(s, c)

        assert(s1 == s)
        assert(c1 == ArrayRemoveOperation(Path, false, 4))
      }
      
      
      "decrement the server index if the server's index is after the client's" in {
        val s = ArrayInsertOperation(Path, false, 3, ServerVal)
        val c = ArrayRemoveOperation(Path, false, 2)
        
        val (s1, c1) = ArrayInsertRemoveTF.transform(s, c)

        assert(s1 == ArrayInsertOperation(Path, false, 2, ServerVal))
        assert(c1 == c)
      }
    }
  }
}