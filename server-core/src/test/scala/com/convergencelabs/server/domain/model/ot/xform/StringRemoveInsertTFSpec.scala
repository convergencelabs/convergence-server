package com.convergencelabs.server.domain.model.ot

import org.scalatest.Finders
import org.scalatest.WordSpec
import org.scalatest.Matchers

// scalastyle:off magic.number multiple.string.literals
class StringRemoveInsertTFSpec
    extends WordSpec
    with Matchers {

  val Path = List(1, 2)

  "A StringRemoveInsertTF" when {

    "tranforming an remove and insert operation " must {

      /**
       * Insert at the start of the remove.
       *
       * Indices         : 0123456789
       * Original String : ABCDEFGHIJ
       *
       * Server OP       :     ^_^         Remove(4,"EFG")
       * Client Op       :    ^            Insert(3,"xx")
       *
       * Client State    : ABCxxCEFGHIJ
       * Server' OP      :       ^_^       Remove(6,"EFG")
       *
       * Server  State   : ABCDGHIJ
       * Client' Op      :    ^            Insert(4,"xx")
       *
       *
       * Converged State: ABCDxxGHIJ
       *
       */
      "increment the server's remove if the client's insert before the start of the remove" in {
        val s = StringRemoveOperation(Path, false, 4, "EFG")
        val c = StringInsertOperation(Path, false, 3, "xx")

        val (s1, c1) = StringRemoveInsertTF.transform(s, c)

        s1 shouldBe StringRemoveOperation(Path, false, 6, "EFG")
        c1 shouldBe c
      }

      /**
       * Insert at the start of the remove.
       *
       * Indices         : 0123456789
       * Original String : ABCDEFGHIJ
       *
       * Server OP       :     ^_^         Remove(4,"EFG")
       * Client Op       :     ^           Insert(4,"xx")
       *
       * Client State    : ABCDxxEFGHIJ
       * Server' OP      :       ^^        Remove(6,"EFG")
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
        val s = StringRemoveOperation(Path, false, 4, "EFG")
        val c = StringInsertOperation(Path, false, 4, "xx")

        val (s1, c1) = StringRemoveInsertTF.transform(s, c)

        s1 shouldBe StringRemoveOperation(Path, false, 6, "EFG")
        c1 shouldBe c
      }

      /**
       * Client insert in the middle of the server remove
       *
       * Indices         : 0123456789
       * Original String : ABCDEFGHIJ
       *
       * Server OP       :    ^-^          Remove(3,"DEF")
       * Client Op       :     ^           Insert(4,"xx")
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

        s1 shouldBe StringRemoveOperation(Path, false, 3, "DxxEF")
        c1 shouldBe StringInsertOperation(Path, true, 4, "xx")
      }

      /**
       * Client insert at the end of the server's remove.
       *
       * Indices         : 0123456789
       * Original String : ABCDEFGHIJ
       *
       * Server OP       :    ^-^          Remove(3,"DEF")
       * Client Op       :      ^          Insert(5,"xx")
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

        s1 shouldBe StringRemoveOperation(Path, false, 3, "DExxF")
        c1 shouldBe StringInsertOperation(Path, true, 5, "xx")
      }

      /**
       * Client insert at the end of the server's remove.
       *
       * Indices         : 0123456789
       * Original String : ABCDEFGHIJ
       *
       * Server OP       :    ^-^          Remove(3,"DEF")
       * Client Op       :       ^         Insert(6,"xx")
       *
       * Client State    : ABCDEFxxGHIJ
       * Server' OP      :    ^-^          Remove(3,"DEF")
       *
       * Server  State   : ABCDxxGHIJ
       * Client' Op      :    ^            Insert(3,"xx")
       *
       *
       * Converged State: ABCxxGHIJ
       *
       * </pre>
       */
      "decrement the index of the insert by the length of the removed string when the insert is after the remove" in {
        val s = StringRemoveOperation(Path, false, 3, "DEF")
        val c = StringInsertOperation(Path, false, 6, "xx")

        val (s1, c1) = StringRemoveInsertTF.transform(s, c)

        s1 shouldBe StringRemoveOperation(Path, false, 3, "DEF")
        c1 shouldBe StringInsertOperation(Path, false, 3, "xx")
      }
    }
  }
}
