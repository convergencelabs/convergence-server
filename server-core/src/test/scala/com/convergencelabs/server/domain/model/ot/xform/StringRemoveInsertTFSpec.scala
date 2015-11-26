package com.convergencelabs.server.domain.model.ot

import org.scalatest.Finders
import org.scalatest.WordSpec

class StringRemoveInsertTFSpec extends WordSpec {

  val Path = List(1, 2)
  val ClientVal = "EF"
  val ServerVal = "yy"

  "A StringRemoveInsertTF" when {

    "tranforming an remove and insert operation " must {

      /**
       * Insert at the start of the remove.
       * 
       * Indices         : 0123456789
       * Original String : ABCDEFGHIJ
       *
       * Client Op       :     ^           Insert(4,"xx")
       * Server OP       :     ^^          Remove(4,"EF")
       *
       * Client State    : ABCDxxEFGHIJ
       * Server' OP      :       ^^        Remove(6,"EF")
       * 
       * Server  State   : ABCDGHIJ
       * Client' Op      :     ^           Insert(4,"xx")
       * 
       *
       * Converged State: ABCDxxGHIJ
       *
       * </pre>
       */
      "increment the server's remove if the client's insert is at the start of the remove" in {
        val s = StringRemoveOperation(Path, false, 4, ServerVal)
        val c = StringInsertOperation(Path, false, 4, ClientVal)

        val (s1, c1) = StringRemoveInsertTF.transform(s, c)

        assert(s1 == StringRemoveOperation(Path, false, 6, ServerVal))
        assert(c1 == c)
      }
      
      /**
       * Insert at the beginning of the delete
       * 
       * Indices         : 0123456789
       * Original String : ABCDEFGHIJ
       *
       * Client Op       :     ^           Insert(4,"xx")
       * Server OP       :     ^^          Remove(4,"EF")
       *
       * Client State    : ABCDxxEFGHIJ
       * Server' OP      :       ^^        Remove(6,"EF")
       * 
       * Server  State   : ABCDGHIJ
       * Client' Op      :     ^           Insert(4,"xx")
       * 
       *
       * Converged State: ABCDxxGHIJ
       *
       * </pre>
       */
      "increment the client remove index if both operations target the same index" in {
        val s = StringRemoveOperation(Path, false, 4, ServerVal)
        val c = StringInsertOperation(Path, false, 4, ClientVal)

        val (s1, c1) = StringRemoveInsertTF.transform(s, c)

        assert(s1 == StringRemoveOperation(Path, false, 6, ServerVal))
        assert(c1 == c)
      }

      /**
       * Client insert in the middle of the server remove
       * 
       * Indices         : 0123456789
       * Original String : ABCDEFGHIJ
       *
       * Client Op       :     ^           Insert(4,"xx")
       * Server OP       :    ^-^          Remove(3,"DEF")
       *
       * Client State    : ABCDxxEFGHIJ
       * Server' OP      :    ^---^        Remove(3,"DxxEF")
       * 
       * Server  State   : ABCDGHIJ
       * Client' Op      :    ^            Insert(4,"xx") - NoOp
       * 
       *
       * Converged State: ABCGHIJ
       *
       * </pre>
       */
      "extend the span of the server's remove to include the client's insert and NoOp the client's insert" in {
        val s = StringRemoveOperation(Path, false, 3, "DEF")
        val c = StringInsertOperation(Path, false, 4, "xx")

        val (s1, c1) = StringRemoveInsertTF.transform(s, c)

        assert(s1 == StringRemoveOperation(Path, false, 3, "DxxEF"))
        assert(c1 == StringInsertOperation(Path, true, 4, "xx"))
      }
      
      /**
       * Client insert at the end of the server's remove.
       * 
       * Indices         : 0123456789
       * Original String : ABCDEFGHIJ
       *
       * Client Op       :      ^          Insert(5,"xx")
       * Server OP       :    ^-^          Remove(3,"DEF")
       *
       * Client State    : ABCDExxFGHIJ
       * Server' OP      :    ^---^        Remove(3,"DExxF")
       * 
       * Server  State   : ABCDGHIJ
       * Client' Op      :     ^            Insert(5,"xx") - NoOp
       * 
       *
       * Converged State: ABCGHIJ
       *
       * </pre>
       */
      "extend the span of the server's remove to include the client's insert and NoOp the client's insert when the insert is at the end of the remove" in {
        val s = StringRemoveOperation(Path, false, 3, "DEF")
        val c = StringInsertOperation(Path, false, 5, "xx")

        val (s1, c1) = StringRemoveInsertTF.transform(s, c)

        assert(s1 == StringRemoveOperation(Path, false, 3, "DExxF"))
        assert(c1 == StringInsertOperation(Path, true, 5, "xx"))
      }
      
      /**
       * Client insert at the end of the server's remove.
       * 
       * Indices         : 0123456789
       * Original String : ABCDEFGHIJ
       *
       * Client Op       :       ^         Insert(6,"xx")
       * Server OP       :    ^-^          Remove(3,"DEF")
       *
       * Client State    : ABCDEFxxGHIJ
       * Server' OP      :    ^-^         Remove(3,"DEF")
       * 
       * Server  State   : ABCDGHIJ
       * Client' Op      :    ^            Insert(3,"xx")
       * 
       *
       * Converged State: ABCxxGHIJ
       *
       * </pre>
       */
      "decrement the index of the insert by the length of the removed string when the insert is after the remove" in {
        val s = StringRemoveOperation(Path, false, 3, "DEF")
        val c = StringInsertOperation(Path, false, 5, "xx")

        val (s1, c1) = StringRemoveInsertTF.transform(s, c)

        assert(s1 == StringRemoveOperation(Path, false, 3, "DExxF"))
        assert(c1 == StringInsertOperation(Path, true, 5, "xx"))
      }
    }
  }
}