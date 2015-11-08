package com.convergencelabs.server.domain.model.ot.cc.xform

import org.json4s.JsonAST.JString
import org.scalatest.Finders
import org.scalatest.WordSpec

import com.convergencelabs.server.domain.model.ot.ops.ArrayInsertOperation
import com.convergencelabs.server.domain.model.ot.xform.ArrayInsertInsertTF

class ArrayInsertInsertTFSpec extends WordSpec {

  val Path = List(1, 2)
  val ClientVal = JString("x")
  val ServerVal = JString("y")

  "A ArrayInsertInsertTF" when {

    "tranforming two insert operations " must {
      
      /**
       * <pre>
       *
       * Indices         : 0123456789
       * Original Array  : ABCDEFGHIJ
       *
       * Client Op       :   ^             Insert(2,"x")
       * Server Op       :   ^             Insert(2,"y")
       *
       * Client State    : ABxCDEFGHIJ
       * Server State    : AByCDEFGHIJ
       *
       * Client Op'      :    ^            Insert(3,"x")
       * Server Op'      :   ^             Insert(2,"y")
       *
       * Converged State : AByxCDEFGHIJ
       *
       * </pre>
       */
      "increment the client's index if both operations target the same index" in {
        val s = ArrayInsertOperation(Path, false, 2, ServerVal)
        val c = ArrayInsertOperation(Path, false, 2, ClientVal)
        
        val (s1, c1) = ArrayInsertInsertTF.transform(s, c)

        assert(s1 == s)
        assert(c1 == ArrayInsertOperation(Path, false, 3, ClientVal))
      }
      
      /**
       * <pre>
       *
       * Indices         : 0123456789
       * Original Array  : ABCDEFGHIJ
       *
       * Client Op       :    ^            Insert(3,"x")
       * Server Op       :   ^             Insert(2,"y")
       *
       * Client State    : ABCxDEFGHIJ
       * Server State    : AByCDEFGHIJ
       *
       * Client Op'      :     ^           Insert(4,"x")
       * Server Op'      :   ^             Insert(2,"y")
       *
       * Converged State : AByCxDEFGHIJ
       *
       * </pre>
       */
      "increment the client's index if the server's index is before the client's" in {
        val s = ArrayInsertOperation(Path, false, 2, ServerVal)
        val c = ArrayInsertOperation(Path, false, 3, ClientVal)
        
        val (s1, c1) = ArrayInsertInsertTF.transform(s, c)

        assert(s1 == s)
        assert(c1 == ArrayInsertOperation(Path, false, 4, ClientVal))
      }
      
      /**
       * <pre>
       *
       * Indices         : 0123456789
       * Original Array  : ABCDEFGHIJ
       *
       * Client Op       :   ^             Insert(2,"x")
       * Server Op       :    ^            Insert(3,"y")
       *
       * Client State    : ABxCDEFGHIJ
       * Server State    : ABCyDEFGHIJ
       *
       * Client Op'      :   ^             Insert(2,"x")
       * Server Op'      :     ^           Insert(4,"y")
       *
       * Converged State : ABxCyDEFGHIJ
       *
       * </pre>
       */
      "increment the server's index if the server's index is after the client's" in {
        val s = ArrayInsertOperation(Path, false, 3, ServerVal)
        val c = ArrayInsertOperation(Path, false, 2, ClientVal)
        
        val (s1, c1) = ArrayInsertInsertTF.transform(s, c)

        assert(s1 == ArrayInsertOperation(Path, false, 4, ServerVal))
        assert(c1 == c)
      }
    }
  }
}