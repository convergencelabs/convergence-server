package com.convergencelabs.server.domain.model.ot

import org.scalatest.Finders
import org.scalatest.WordSpec
import org.scalatest.Matchers

// scalastyle:off magic.number multiple.string.literals
class StringRemoveRemoveTFSpec
    extends WordSpec
    with Matchers {

  val Path = List(1, 2)

  "A StringRemoveRemoveTF" when {

    "tranforming a server remove against a client remove " must {

      /**
       * Identical removes.
       *
       * Indices         : 0123456789
       * Original String : ABCDEFGHIJ
       *
       * Server OP       :     ^^          Remove(4,"EF")
       * Client Op       :     ^^          Remove(4,"EF")
       *
       * Client State    : ABCDGHIJ
       * Server' OP      :     ^^          Remove(4,"EF") - NoOp
       *
       * Server  State   : ABCDGHIJ
       * Client' Op      :     ^^          Remove(4,"EF") - NoOp
       *
       *
       * Converged State: ABCDGHIJ
       */
      "noOp both operations if they are equal" in {
        val s = StringRemoveOperation(Path, false, 4, "EF")
        val c = StringRemoveOperation(Path, false, 4, "EF")

        val (s1, c1) = StringRemoveRemoveTF.transform(s, c)

        s1 shouldBe StringRemoveOperation(Path, true, 4, "EF")
        c1 shouldBe StringRemoveOperation(Path, true, 4, "EF")
      }

      /**
       * The client delete starts the server delete.
       *
       * Indices         : 0123456789
       * Original String : ABCDEFGHIJ
       *
       * Server OP       :    ^-^          Remove(3,"DEF")
       * Client Op       :    ^^           Remove(3,"DE")
       *
       * Client State    : ABCEFGHIJ
       * Server' OP      :    ^            Remove(3,"F")
       *
       * Server  State   : ABCDGHIJ
       * Client' Op      :     ^^          Remove(3,"DE") - NoOp
       *
       *
       * Converged State: ABCGHIJ
       */
      "shorten the server op and noOp the client op if the client op starts the server op" in {
        val s = StringRemoveOperation(Path, false, 3, "DEF")
        val c = StringRemoveOperation(Path, false, 3, "DE")

        val (s1, c1) = StringRemoveRemoveTF.transform(s, c)

        s1 shouldBe StringRemoveOperation(Path, false, 3, "F")
        c1 shouldBe StringRemoveOperation(Path, true, 3, "DE")
      }

      /**
       * The client delete starts the server delete.
       *
       * Indices         : 0123456789
       * Original String : ABCDEFGHIJ
       *
       * Server OP       :    ^^           Remove(3,"DE")
       * Client Op       :    ^-^          Remove(3,"DEF")
       *
       * Client State    : ABCEFGHIJ
       * Server' OP      :    ^            Remove(3,"DE") - NoOp
       *
       * Server  State   : ABCDGHIJ
       * Client' Op      :     ^^          Remove(3,"F")
       *
       *
       * Converged State: ABCGHIJ
       */
      "shorten the client op and noOp the esrvr op if the server op starts the client op" in {
        val s = StringRemoveOperation(Path, false, 3, "DE")
        val c = StringRemoveOperation(Path, false, 3, "DEF")

        val (s1, c1) = StringRemoveRemoveTF.transform(s, c)

        s1 shouldBe StringRemoveOperation(Path, true, 3, "DE")
        c1 shouldBe StringRemoveOperation(Path, false, 3, "F")
      }

      /**
       * The client remove is before the servers
       *
       * Indices         : 0123456789
       * Original String : ABCDEFGHIJ
       *
       * Server OP       :       ^_^       Remove(6,"GHI")
       * Client Op       :  ^-^            Remove(1,"BCD")
       *
       * Client State    : AEFGHIJ
       * Server' OP      :    ^_^          Remove(3,"GHI")
       *
       * Server  State   : ABCDEFJ
       * Client' Op      :  ^_^            Remove(1,"BCD")
       *
       * Converged State: AEFJ
       */
      "decrease the server op index and not transform the client op if the client is before the server" in {
        val s = StringRemoveOperation(Path, false, 6, "GHI")
        val c = StringRemoveOperation(Path, false, 1, "BCD")

        val (s1, c1) = StringRemoveRemoveTF.transform(s, c)

        s1 shouldBe StringRemoveOperation(Path, false, 3, "GHI")
        c1 shouldBe StringRemoveOperation(Path, false, 1, "BCD")
      }

      /**
       * The server remove is before the client
       *
       * Indices         : 0123456789
       * Original String : ABCDEFGHIJ
       *
       * Server OP       :  ^_^            Remove(1,"BCD")
       * Client Op       :       ^-^       Remove(6,"GHI")
       *
       * Client State    : ABCDEFJ
       * Server' OP      :  ^_^            Remove(1,"BCD")
       *
       * Server  State   : AEFGHIJ
       * Client' Op      :    ^_^          Remove(3,"GHI")
       *
       * Converged State: AEFJ
       */
      "decrease the client op index and not transform the server op if the server is before the client" in {
        val s = StringRemoveOperation(Path, false, 1, "BCD")
        val c = StringRemoveOperation(Path, false, 6, "GHI")

        val (s1, c1) = StringRemoveRemoveTF.transform(s, c)

        s1 shouldBe StringRemoveOperation(Path, false, 1, "BCD")
        c1 shouldBe StringRemoveOperation(Path, false, 3, "GHI")
      }

      /**
       * The server remove is before the client
       *
       * Indices         : 0123456789
       * Original String : ABCDEFGHIJ
       *
       * Server OP       :    ^_^          Remove(3,"DEF")
       * Client Op       :     ^^          Remove(4,"EF")
       *
       * Client State    : ABCDGHIJ
       * Server' OP      :    ^            Remove(3,"D")
       *
       * Server  State   : ABCGHIJ
       * Client' Op      :     ^^          Remove(4,"EF") - NoOp
       *
       * Converged State: AEFJ
       */
      "noOp the client operation and shorten the server op when the client op finishes the server op" in {
        val s = StringRemoveOperation(Path, false, 3, "DEF")
        val c = StringRemoveOperation(Path, false, 4, "EF")

        val (s1, c1) = StringRemoveRemoveTF.transform(s, c)

        s1 shouldBe StringRemoveOperation(Path, false, 3, "D")
        c1 shouldBe StringRemoveOperation(Path, true, 4, "EF")
      }

      /**
       * The server remove is before the client
       *
       * Indices         : 0123456789
       * Original String : ABCDEFGHIJ
       *
       * Server OP       :     ^^          Remove(4,"EF")
       * Client Op       :    ^_^          Remove(3,"DEF")
       *
       * Client State    : ABCGHIJ
       * Server' OP      :    ^            Remove(4,"EF") - NoOp
       *
       * Server  State   : ABCDGHIJ
       * Client' Op      :    ^            Remove(3,"D")
       *
       * Converged State: AEFJ
       */
      "noOp the server operation and shorten the client op when the server op finishes the client op" in {
        val s = StringRemoveOperation(Path, false, 4, "EF")
        val c = StringRemoveOperation(Path, false, 3, "DEF")

        val (s1, c1) = StringRemoveRemoveTF.transform(s, c)

        s1 shouldBe StringRemoveOperation(Path, true, 4, "EF")
        c1 shouldBe StringRemoveOperation(Path, false, 3, "D")
      }

      /**
       * The client spans the server operation
       *
       * Indices         : 0123456789
       * Original String : ABCDEFGHIJ
       *
       * Server OP       :     ^^          Remove(4,"EF")
       * Client Op       :    ^__^         Remove(3,"DEFG")
       *
       * Client State    : ABCHIJ
       * Server' OP      :     ^^          Remove(4,"EF") - NoOp
       *
       * Server  State   : ABCDGHIJ
       * Client' Op      :    ^^           Remove(3,"DG")
       *
       * Converged State: ABCHIJ
       */
      "noOp the server op and shorten the client op if the client op spans the servers op" in {
        val s = StringRemoveOperation(Path, false, 4, "EF")
        val c = StringRemoveOperation(Path, false, 3, "DEFG")

        val (s1, c1) = StringRemoveRemoveTF.transform(s, c)

        s1 shouldBe StringRemoveOperation(Path, true, 4, "EF")
        c1 shouldBe StringRemoveOperation(Path, false, 3, "DG")
      }

      /**
       * The server spans the client operation
       *
       * Indices         : 0123456789
       * Original String : ABCDEFGHIJ
       *
       * Server OP       :    ^__^         Remove(3,"DEFG")
       * Client Op       :     ^^          Remove(4,"EF")
       *
       * Client State    : ABCDGHIJ
       * Server' OP      :    ^^           Remove(3,"DG")
       *
       * Server  State   : ABCHIJ
       * Client' Op      :     ^^          Remove(4,"EF") - NoOp
       *
       * Converged State: ABCHIJ
       */
      "noOp the client op and shorten the server op if the server op spans the client op" in {
        val s = StringRemoveOperation(Path, false, 3, "DEFG")
        val c = StringRemoveOperation(Path, false, 4, "EF")

        val (s1, c1) = StringRemoveRemoveTF.transform(s, c)

        s1 shouldBe StringRemoveOperation(Path, false, 3, "DG")
        c1 shouldBe StringRemoveOperation(Path, true, 4, "EF")
      }

      /**
       * The server overlaps into the client op
       *
       * Indices         : 0123456789
       * Original String : ABCDEFGHIJ
       *
       * Server OP       :   ^__^          Remove(2,"CDEF")
       * Client Op       :     ^__^        Remove(4,"EFGH")
       *
       * Client State    : ABCDIJ
       * Server' OP      :   ^^            Remove(2,"CD")
       *
       * Server  State   : ABGHIJ
       * Client' Op      :   ^^            Remove(2,"GH")
       *
       * Converged State: ABIJ
       */
      "trim the end of the server op and the begining of the client op if the server op overlaps into the client op" in {
        val s = StringRemoveOperation(Path, false, 2, "CDEF")
        val c = StringRemoveOperation(Path, false, 4, "EFGH")

        val (s1, c1) = StringRemoveRemoveTF.transform(s, c)

        s1 shouldBe StringRemoveOperation(Path, false, 2, "CD")
        c1 shouldBe StringRemoveOperation(Path, false, 2, "GH")
      }

      /**
       * The server overlaps into the client op
       *
       * Indices         : 0123456789
       * Original String : ABCDEFGHIJ
       *
       * Server OP       :     ^__^        Remove(4,"EFGH")
       * Client Op       :   ^__^          Remove(2,"CDEF")
       *
       * Client State    : ABGHIJ
       * Server' OP      :   ^^            Remove(2,"GH")
       *
       * Server  State   : ABCDIJ
       * Client' Op      :   ^^            Remove(2,"CD")
       *
       * Converged State: ABIJ
       */
      "trim the end of the client op and the begining of the server op if the client op overlaps into the server op" in {
        val s = StringRemoveOperation(Path, false, 4, "EFGH")
        val c = StringRemoveOperation(Path, false, 2, "CDEF")

        val (s1, c1) = StringRemoveRemoveTF.transform(s, c)

        s1 shouldBe StringRemoveOperation(Path, false, 2, "GH")
        c1 shouldBe StringRemoveOperation(Path, false, 2, "CD")
      }
    }
  }
}
