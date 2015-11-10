package com.convergencelabs.server.domain.model.ot.xform

import org.json4s.JsonAST.JString
import org.scalatest.Finders
import org.scalatest.WordSpec

import com.convergencelabs.server.domain.model.ot.ops.ArrayRemoveOperation

class ArrayRemoveRemoveTFSpec extends WordSpec {

  val Path = List(1, 2)
  val ClientVal = JString("x")
  val ServerVal = JString("y")

  "A ArrayRemoveRemoveTF" when {
    "tranforming a remove against a remove" must {
      
      /**
       * <pre>
       *
       * Indices         : [0, 1, 2, 3, 4, 5, 6, 7, 8, 9]
       * Original Array  : [A, B, C, D, E, F, G, H, I, J]
       *
       * Server Op       :        ^                              Remove(2, C)
       * Client Op       :           ^                           Remove(3, D)
       *
       * Server State    : [A, B, D, E, F, G, H, I, J]
       * Client Op'      :        ^                              Remove(2, D)
       * 
       * Client State    : [A, B, C, E, F, G, H, I, J]
       * Server Op'      :        ^                              Remove(2, C)
       *
       * Converged State : [A, B, E, F, G, H, I, J]
       *
       * </pre>
       */
      "decrement the client's index if the server's index is less than the client's index" in {
        val s = ArrayRemoveOperation(Path, false, 2)
        val c = ArrayRemoveOperation(Path, false, 3)
        
        val (s1, c1) = ArrayRemoveRemoveTF.transform(s, c)

        assert(s1 == s)
        assert(c1 == ArrayRemoveOperation(Path, false, 2))
      }
      
      /**
       * <pre>
       *
       * Indices         : [0, 1, 2, 3, 4, 5, 6, 7, 8, 9]
       * Original Array  : [A, B, C, D, E, F, G, H, I, J]
       *
       * Server Op       :        ^                              Remove(2, C)
       * Client Op       :        ^                              Remove(2, C)
       *
       * Server State    : [A, B, D, E, F, G, H, I, J]
       * Client Op'      :        ^                              Remove(2, C) - NoOp
       * 
       * Client State    : [A, B, D, E, F, G, H, I, J]
       * Server Op'      :        ^                              Remove(2, C) - NoOp
       * 
       * Converged State : [A, B, D, E, F, G, H, I, J]
       *
       * </pre>
       */
      "decrement the client's index if the server's index is equal to the client's index" in {
        val s = ArrayRemoveOperation(Path, false, 2)
        val c = ArrayRemoveOperation(Path, false, 2)
        
        val (s1, c1) = ArrayRemoveRemoveTF.transform(s, c)

        assert(s1 == ArrayRemoveOperation(Path, true, 2))
        assert(c1 == ArrayRemoveOperation(Path, true, 2))
      }
      
      /**
       * <pre>
       *
       * Indices         : [0, 1, 2, 3, 4, 5, 6, 7, 8, 9]
       * Original Array  : [A, B, C, D, E, F, G, H, I, J]
       *
       * Server Op       :              ^                        Remove(4, E)
       * Client Op       :           ^                           Remove(3, D)
       *
       * Server State    : [A, B, C, D, F, G, H, I, J]
       * Client Op'      :           ^                           Remove(3, D)
       * 
       * Client State    : [A, B, C, E, F, G, H, I, J]
       * Server Op'      :           ^                           Remove(3, E)
       * 
       * Converged State : [A, B, X, C, D, F, G, H, I, J]
       *
       * </pre>
       */
      "increment the server's index if the server's index is greater than the client's index" in {
        val s = ArrayRemoveOperation(Path, false, 4)
        val c = ArrayRemoveOperation(Path, false, 3)
        
        val (s1, c1) = ArrayRemoveRemoveTF.transform(s, c)

        assert(s1 == ArrayRemoveOperation(Path, false, 3))
        assert(c1 == c)
      }
    }
  }
}