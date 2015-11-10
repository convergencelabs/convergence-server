package com.convergencelabs.server.domain.model.ot.xform

import org.json4s.JsonAST.JString
import org.scalatest.Finders
import org.scalatest.WordSpec

import com.convergencelabs.server.domain.model.ot.ops.ArrayInsertOperation
import com.convergencelabs.server.domain.model.ot.ops.ArrayRemoveOperation
import com.convergencelabs.server.domain.model.ot.ops.ArrayReplaceOperation

class ArrayRemoveReplaceTFSpec extends WordSpec {

  val Path = List(1, 2)
  val ClientVal = JString("x")
  val ServerVal = JString("y")

  "A ArrayRemoveReplaceTF" when {
    "tranforming a remove against a remove" must {
      
      /**
       * <pre>
       *
       * Indices         : [0, 1, 2, 3, 4, 5, 6, 7, 8, 9]
       * Original Array  : [A, B, C, D, E, F, G, H, I, J]
       *
       * Server Op       :        ^                              Remove(2, C)
       * Client Op       :           ^                           Replace(3, D, X)
       *
       * Server State    : [A, B, D, E, F, G, H, I, J]
       * Client Op'      :        ^                              Replace(2, D, X)
       * 
       * Client State    : [A, B, C, X, E, F, G, H, I, J]
       * Server Op'      :        ^                              Remove(2, C)
       *
       * Converged State : [A, B, E, F, G, H, I, J]
       *
       * </pre>
       */
      "decrement the client's index if the server's index is less than the client's index" in {
        val s = ArrayRemoveOperation(Path, false, 2)
        val c = ArrayReplaceOperation(Path, false, 3, ClientVal)
        
        val (s1, c1) = ArrayRemoveReplaceTF.transform(s, c)

        assert(s1 == s)
        assert(c1 == ArrayReplaceOperation(Path, false, 2, ClientVal))
      }
      
      /**
       * <pre>
       *
       * Indices         : [0, 1, 2, 3, 4, 5, 6, 7, 8, 9]
       * Original Array  : [A, B, C, D, E, F, G, H, I, J]
       *
       * Server Op       :        ^                              Remove(2, C)
       * Client Op       :        ^                              Replace(2, C, X)
       *
       * Server State    : [A, B, D, E, F, G, H, I, J]
       * Client Op'      :        ^                              Insert(2, X)
       * 
       * Client State    : [A, B, X, D, E, F, G, H, I, J]
       * Server Op'      :        ^                              Remove(2, C) - NoOp
       * 
       * Converged State : [A, B, X, D, E, F, G, H, I, J]
       *
       * </pre>
       */
      "noOp ther server's remove and convert the client's replace to an insert if the server's index is equal to the client's index" in {
        val s = ArrayRemoveOperation(Path, false, 2)
        val c = ArrayReplaceOperation(Path, false, 2, ClientVal)
        
        val (s1, c1) = ArrayRemoveReplaceTF.transform(s, c)

        assert(s1 == ArrayRemoveOperation(Path, true, 2))
        assert(c1 == ArrayInsertOperation(Path, false, 2, ClientVal))
      }
      
      /**
       * <pre>
       *
       * Indices         : [0, 1, 2, 3, 4, 5, 6, 7, 8, 9]
       * Original Array  : [A, B, C, D, E, F, G, H, I, J]
       *
       * Server Op       :              ^                        Remove(4, E)
       * Client Op       :           ^                           Replace(3, D, X)
       *
       * Server State    : [A, B, C, D, F, G, H, I, J]
       * Client Op'      :           ^                           Replace(3, D, X)
       * 
       * Client State    : [A, B, C, X, E, F, G, H, I, J]
       * Server Op'      :              ^                        Remove(4, E)
       * 
       * Converged State : [A, B, X, C, D, F, G, H, I, J]
       *
       * </pre>
       */
      "transform neither operation if the server's remove index is greater than the client's replace index" in {
        val s = ArrayRemoveOperation(Path, false, 4)
        val c = ArrayReplaceOperation(Path, false, 3, ClientVal)
        
        val (s1, c1) = ArrayRemoveReplaceTF.transform(s, c)

        assert(s1 == s)
        assert(c1 == c)
      }
    }
  }
}