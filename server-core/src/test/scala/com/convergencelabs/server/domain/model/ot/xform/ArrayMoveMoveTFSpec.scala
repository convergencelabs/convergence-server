package com.convergencelabs.server.domain.model.ot

import org.json4s.JsonAST.JString
import org.scalatest.Finders
import org.scalatest.Matchers
import org.scalatest.WordSpec

// scalastyle:off magic.number multiple.string.literals file.size.limit
class ArrayMoveMoveTFSpec
    extends WordSpec
    with Matchers {

  val Path = List(1, 2)

  "A ArrayMoveMoveTF" when {
    "tranforming a server forward move against a client forward move" must {

      /**
       * A-MM-FF-1
       */
      "not transform either operation when the server's move preceeds the client's move" in {
        val s = ArrayMoveOperation(Path, false, 2, 4)
        val c = ArrayMoveOperation(Path, false, 5, 7)

        val (s1, c1) = ArrayMoveMoveTF.transform(s, c)

        s1 shouldBe s
        c1 shouldBe c
      }

      /**
       * A-MM-FF-2
       */
      "not transform either operation when the server's move is preceded by the client's move" in {
        val s = ArrayMoveOperation(Path, false, 5, 7)
        val c = ArrayMoveOperation(Path, false, 2, 4)

        val (s1, c1) = ArrayMoveMoveTF.transform(s, c)

        s1 shouldBe s
        c1 shouldBe c
      }

      /**
       * A-MM-FF-3
       */
      "decrement the server move's toIndex and decrement the client move's fromIndex, when the server move meets the client move" in {
        val s = ArrayMoveOperation(Path, false, 3, 5)
        val c = ArrayMoveOperation(Path, false, 5, 7)

        val (s1, c1) = ArrayMoveMoveTF.transform(s, c)

        s1 shouldBe ArrayMoveOperation(Path, false, 3, 4)
        c1 shouldBe ArrayMoveOperation(Path, false, 4, 7)
      }

      /**
       * A-MM-FF-4
       */
      "decrement the server move's fromIndex and decrement the client move's toIndex, when the server move is met by the client move" in {
        val s = ArrayMoveOperation(Path, false, 5, 7)
        val c = ArrayMoveOperation(Path, false, 3, 5)

        val (s1, c1) = ArrayMoveMoveTF.transform(s, c)

        s1 shouldBe ArrayMoveOperation(Path, false, 4, 7)
        c1 shouldBe ArrayMoveOperation(Path, false, 3, 4)
      }

      /**
       * A-MM-FF-5
       */
      "decrement the server move's toIndex and decrement the client move's fromIndex, when the server move overlaps the client move" in {
        val s = ArrayMoveOperation(Path, false, 3, 5)
        val c = ArrayMoveOperation(Path, false, 4, 6)

        val (s1, c1) = ArrayMoveMoveTF.transform(s, c)

        s1 shouldBe ArrayMoveOperation(Path, false, 3, 4)
        c1 shouldBe ArrayMoveOperation(Path, false, 3, 6)
      }

      /**
       * A-MM-FF-6
       */
      "decrement the server move's fromIndex and decrement the client move's toIndex when the server move is overlapped by the client move" in {
        val s = ArrayMoveOperation(Path, false, 4, 6)
        val c = ArrayMoveOperation(Path, false, 3, 5)

        val (s1, c1) = ArrayMoveMoveTF.transform(s, c)

        s1 shouldBe ArrayMoveOperation(Path, false, 3, 6)
        c1 shouldBe ArrayMoveOperation(Path, false, 3, 4)
      }

      /**
       * A-MM-FF-7
       */
      "set the server move's fromIndex to the client move's toIndex and noOp the client when the server move starts the client move" in {
        val s = ArrayMoveOperation(Path, false, 3, 4)
        val c = ArrayMoveOperation(Path, false, 3, 6)

        val (s1, c1) = ArrayMoveMoveTF.transform(s, c)

        s1 shouldBe ArrayMoveOperation(Path, false, 6, 4)
        c1 shouldBe ArrayMoveOperation(Path, true, 3, 6)
      }

      /**
       * A-MM-FF-8
       */
      "set the server move's fromIndex to the client move's toIndex and noOp the client when the server move is started by the client move" in {
        val s = ArrayMoveOperation(Path, false, 3, 6)
        val c = ArrayMoveOperation(Path, false, 3, 4)

        val (s1, c1) = ArrayMoveMoveTF.transform(s, c)

        s1 shouldBe ArrayMoveOperation(Path, false, 4, 6)
        c1 shouldBe ArrayMoveOperation(Path, true, 3, 4)
      }

      /**
       * A-MM-FF-9
       */
      "not transform the server move and decrement the client move's fromIndex and toIndex, when the server move contains the client move" in {
        val s = ArrayMoveOperation(Path, false, 3, 6)
        val c = ArrayMoveOperation(Path, false, 4, 5)

        val (s1, c1) = ArrayMoveMoveTF.transform(s, c)

        s1 shouldBe s
        c1 shouldBe ArrayMoveOperation(Path, false, 3, 4)
      }

      /**
       * A-MM-FF-10
       */
      "decrement the server move's fromIndex and toIndex and not transform the client move, when the server move is contained by the client move" in {
        val s = ArrayMoveOperation(Path, false, 4, 5)
        val c = ArrayMoveOperation(Path, false, 3, 6)

        val (s1, c1) = ArrayMoveMoveTF.transform(s, c)

        s1 shouldBe ArrayMoveOperation(Path, false, 3, 4)
        c1 shouldBe c
      }

      /**
       * A-MM-FF-11
       */
      "decrement the server move's fromIndex and the client move's toIndex, when the server move finishes the client move" in {
        val s = ArrayMoveOperation(Path, false, 5, 6)
        val c = ArrayMoveOperation(Path, false, 3, 6)

        val (s1, c1) = ArrayMoveMoveTF.transform(s, c)

        s1 shouldBe ArrayMoveOperation(Path, false, 4, 6)
        c1 shouldBe ArrayMoveOperation(Path, false, 3, 5)
      }

      /**
       * A-MM-FF-12
       */
      "not transform the server move and decrement the client move's fromIndex and toIndex, when the server move finishes the client move" in {
        val s = ArrayMoveOperation(Path, false, 3, 6)
        val c = ArrayMoveOperation(Path, false, 5, 6)

        val (s1, c1) = ArrayMoveMoveTF.transform(s, c)

        s1 shouldBe s
        c1 shouldBe ArrayMoveOperation(Path, false, 4, 5)
      }

      /**
       * A-MM-FF-13
       */
      "noOp both the client and server operation, when the server move is equal to the client move" in {
        val s = ArrayMoveOperation(Path, false, 3, 5)
        val c = ArrayMoveOperation(Path, false, 3, 5)

        val (s1, c1) = ArrayMoveMoveTF.transform(s, c)

        s1 shouldBe ArrayMoveOperation(Path, true, 3, 5)
        c1 shouldBe ArrayMoveOperation(Path, true, 3, 5)
      }
    }

    "tranforming a server forward move against a client backward move" must {
      /**
       * A-MM-FB-1 - Server Forward Move Precedes Client Backward Move
       */
      "not transform either operation when the server's move preceeds the client's move" in {
        val s = ArrayMoveOperation(Path, false, 2, 4)
        val c = ArrayMoveOperation(Path, false, 7, 5)

        val (s1, c1) = ArrayMoveMoveTF.transform(s, c)

        s1 shouldBe s
        c1 shouldBe c
      }

      /**
       * A-MM-FB-2 - Server Forward Move Preceded By Client Backward Move
       */
      "not transform either operation when the server's move is preceded by the client's move" in {
        val s = ArrayMoveOperation(Path, false, 5, 7)
        val c = ArrayMoveOperation(Path, false, 4, 2)

        val (s1, c1) = ArrayMoveMoveTF.transform(s, c)

        s1 shouldBe s
        c1 shouldBe c
      }

      /**
       * A-MM-FB-3 - Server Forward Move Meets Client Backward Move
       */
      "decrement the server move's toIndex and decrement the client move's fromIndex when the server move meets the client move" in {
        val s = ArrayMoveOperation(Path, false, 3, 5)
        val c = ArrayMoveOperation(Path, false, 7, 5)

        val (s1, c1) = ArrayMoveMoveTF.transform(s, c)

        s1 shouldBe ArrayMoveOperation(Path, false, 3, 6)
        c1 shouldBe ArrayMoveOperation(Path, false, 7, 4)
      }

      /**
       * A-MM-FB-4 - Server Forward Move Met By Client Backward Move
       */
      "set the server move's fromIndex to the client move's toIndex and noOp the client move, when the server move is met by the client move" in {
        val s = ArrayMoveOperation(Path, false, 5, 7)
        val c = ArrayMoveOperation(Path, false, 5, 3)

        val (s1, c1) = ArrayMoveMoveTF.transform(s, c)

        s1 shouldBe ArrayMoveOperation(Path, false, 3, 7)
        c1 shouldBe ArrayMoveOperation(Path, true, 5, 3)
      }

      /**
       * A-MM-FB-5 - Server Forward Move Overlaps Client Backward Move
       */
      "increment the server move's toIndex and decrement the client move's toIndex, when the server move overlaps the client move" in {
        val s = ArrayMoveOperation(Path, false, 3, 5)
        val c = ArrayMoveOperation(Path, false, 6, 4)

        val (s1, c1) = ArrayMoveMoveTF.transform(s, c)

        s1 shouldBe ArrayMoveOperation(Path, false, 3, 6)
        c1 shouldBe ArrayMoveOperation(Path, false, 6, 3)
      }

      /**
       * A-MM-FB-6 - Server Forward Move Overlapped By Client Backward Move
       */
      "increment the server move's fromIndex and decrement the client move's fromIndex, when the server move is overlapped by the client move" in {
        val s = ArrayMoveOperation(Path, false, 4, 6)
        val c = ArrayMoveOperation(Path, false, 5, 3)

        val (s1, c1) = ArrayMoveMoveTF.transform(s, c)

        s1 shouldBe ArrayMoveOperation(Path, false, 5, 6)
        c1 shouldBe ArrayMoveOperation(Path, false, 4, 3)
      }

      /**
       * A-MM-FB-7 - Server Forward Move Starts Client Backward Move
       */
      "increment the server move's fromIndex and toIndex and not transform the client move, when the server move starts the client move" in {
        val s = ArrayMoveOperation(Path, false, 3, 5)
        val c = ArrayMoveOperation(Path, false, 6, 3)

        val (s1, c1) = ArrayMoveMoveTF.transform(s, c)

        s1 shouldBe ArrayMoveOperation(Path, false, 4, 6)
        c1 shouldBe c
      }

      /**
       * A-MM-FB-8 - Server Forward Move Started By Client Backward Move
       */
      "increment the server move's fromIndex and decrement the client move's fromInex, when the server move is started by the client move" in {
        val s = ArrayMoveOperation(Path, false, 3, 6)
        val c = ArrayMoveOperation(Path, false, 5, 3)

        val (s1, c1) = ArrayMoveMoveTF.transform(s, c)

        s1 shouldBe ArrayMoveOperation(Path, false, 4, 6)
        c1 shouldBe ArrayMoveOperation(Path, false, 4, 3)
      }

      /**
       * A-MM-FB-9 - Server Forward Move Contains Client Backward Move
       */
      "not transform the server move and decrement the client move's fromIndex and toIndex, when the server move contains the client move" in {
        val s = ArrayMoveOperation(Path, false, 3, 6)
        val c = ArrayMoveOperation(Path, false, 5, 4)

        val (s1, c1) = ArrayMoveMoveTF.transform(s, c)

        s1 shouldBe s
        c1 shouldBe ArrayMoveOperation(Path, false, 4, 3)
      }

      /**
       * A-MM-FB-10 - Server Forward Move Contained By Client Backward Move
       */
      "increment the server move's fromIndex and toIndex and not transform the client move, when the server move is contained by the client move" in {
        val s = ArrayMoveOperation(Path, false, 4, 5)
        val c = ArrayMoveOperation(Path, false, 6, 3)

        val (s1, c1) = ArrayMoveMoveTF.transform(s, c)

        s1 shouldBe ArrayMoveOperation(Path, false, 5, 6)
        c1 shouldBe c
      }

      /**
       * A-MM-FB-11 - Server Forward Move Finishes Client Backward Move
       */
      "decrement the server move's fromIndex and the client move's fromIndex, when the server move finishes the client move" in {
        val s = ArrayMoveOperation(Path, false, 4, 6)
        val c = ArrayMoveOperation(Path, false, 6, 2)

        val (s1, c1) = ArrayMoveMoveTF.transform(s, c)

        s1 shouldBe ArrayMoveOperation(Path, false, 5, 6)
        c1 shouldBe ArrayMoveOperation(Path, false, 5, 2)
      }

      /**
       * A-MM-FB-12 - Server Forward Move Finished By Client Backward Move
       */
      "not transform the server move and decrement the client move's fromIndex and toIndex, when the server move finishes the client move" in {
        val s = ArrayMoveOperation(Path, false, 2, 6)
        val c = ArrayMoveOperation(Path, false, 6, 4)

        val (s1, c1) = ArrayMoveMoveTF.transform(s, c)

        s1 shouldBe s
        c1 shouldBe ArrayMoveOperation(Path, false, 5, 3)
      }

      /**
       * A-MM-FB-13 - Server Forward Move Equal To Client Backward Move
       */
      "increment the server move's fromIndex and decrement the client move's fromIndex, when the server move is equal to the client move" in {
        val s = ArrayMoveOperation(Path, false, 2, 6)
        val c = ArrayMoveOperation(Path, false, 6, 2)

        val (s1, c1) = ArrayMoveMoveTF.transform(s, c)

        s1 shouldBe ArrayMoveOperation(Path, false, 3, 6)
        c1 shouldBe ArrayMoveOperation(Path, false, 5, 2)
      }
    }

    "tranforming a server forward move against a client identity move" must {

      /**
       * A-MM-FI-1
       */
      "not transform either operation when the server's move is before the client's move" in {
        val s = ArrayMoveOperation(Path, false, 3, 5)
        val c = ArrayMoveOperation(Path, false, 2, 2)

        val (s1, c1) = ArrayMoveMoveTF.transform(s, c)

        s1 shouldBe s
        c1 shouldBe c
      }

      /**
       * A-MM-FI-2
       */
      "not transform the server operation and move the client's move to the toIndex of the server's move, when the client move is at the start of the server's move" in {
        val s = ArrayMoveOperation(Path, false, 3, 5)
        val c = ArrayMoveOperation(Path, false, 3, 3)

        val (s1, c1) = ArrayMoveMoveTF.transform(s, c)

        s1 shouldBe s
        c1 shouldBe ArrayMoveOperation(Path, false, 5, 5)
      }

      /**
       * A-MM-FI-3
       */
      "not transform the server operation and shift the client's move one to the left, when the client move is within the server's move" in {
        val s = ArrayMoveOperation(Path, false, 3, 5)
        val c = ArrayMoveOperation(Path, false, 4, 4)

        val (s1, c1) = ArrayMoveMoveTF.transform(s, c)

        s1 shouldBe s
        c1 shouldBe ArrayMoveOperation(Path, false, 3, 3)
      }

      /**
       * A-MM-FI-4
       */
      "not transform the server operation and shift the client's move one to the left, when the client move is at the end of the server's move" in {
        val s = ArrayMoveOperation(Path, false, 3, 5)
        val c = ArrayMoveOperation(Path, false, 5, 5)

        val (s1, c1) = ArrayMoveMoveTF.transform(s, c)

        s1 shouldBe s
        c1 shouldBe ArrayMoveOperation(Path, false, 4, 4)
      }

      /**
       * A-MM-FI-5
       */
      "not transform either operation, when the client's move is after the server's move" in {
        val s = ArrayMoveOperation(Path, false, 3, 5)
        val c = ArrayMoveOperation(Path, false, 6, 6)

        val (s1, c1) = ArrayMoveMoveTF.transform(s, c)

        s1 shouldBe s
        c1 shouldBe c
      }
    }

    "tranforming a server backward move against a client forward move" must {
      /**
       * A-MM-BF-1 - Server Backward Move Precedes Client Forward Move
       */
      "not transform either operation when the server's move preceeds the client's move" in {
        val s = ArrayMoveOperation(Path, false, 4, 2)
        val c = ArrayMoveOperation(Path, false, 5, 7)

        val (s1, c1) = ArrayMoveMoveTF.transform(s, c)

        s1 shouldBe s
        c1 shouldBe c
      }

      /**
       * A-MM-BF-2 - Server Backward Move Preceded By Client Forward Move
       */
      "not transform either operation when the server's move is preceded by the client's move" in {
        val s = ArrayMoveOperation(Path, false, 7, 5)
        val c = ArrayMoveOperation(Path, false, 2, 4)

        val (s1, c1) = ArrayMoveMoveTF.transform(s, c)

        s1 shouldBe s
        c1 shouldBe c
      }

      /**
       * A-MM-FB-3 - Server Backward Move Meets Client Forward Move
       */
      "decrement the server move's toIndex and decrement the client move's fromIndex when the server move meets the client move" in {
        val s = ArrayMoveOperation(Path, false, 5, 3)
        val c = ArrayMoveOperation(Path, false, 5, 7)

        val (s1, c1) = ArrayMoveMoveTF.transform(s, c)

        s1 shouldBe ArrayMoveOperation(Path, false, 7, 3)
        c1 shouldBe ArrayMoveOperation(Path, true, 5, 7)
      }

      /**
       * A-MM-BF-4 - Server Backward Move Met By Client Forward Move
       */
      "set the server move's fromIndex to the client move's toIndex and noOp the client move, when the server move is met by the client move" in {
        val s = ArrayMoveOperation(Path, false, 7, 5)
        val c = ArrayMoveOperation(Path, false, 3, 5)

        val (s1, c1) = ArrayMoveMoveTF.transform(s, c)

        s1 shouldBe ArrayMoveOperation(Path, false, 7, 4)
        c1 shouldBe ArrayMoveOperation(Path, false, 3, 6)
      }

      /**
       * A-MM-BF-5 - Server Backward Move Overlaps Client Forward Move
       */
      "increment the server move's toIndex and decrement the client move's toIndex, when the server move overlaps the client move" in {
        val s = ArrayMoveOperation(Path, false, 5, 3)
        val c = ArrayMoveOperation(Path, false, 4, 6)

        val (s1, c1) = ArrayMoveMoveTF.transform(s, c)

        s1 shouldBe ArrayMoveOperation(Path, false, 4, 3)
        c1 shouldBe ArrayMoveOperation(Path, false, 5, 6)
      }

      /**
       * A-MM-BF-6 - Server Backward Move Overlapped By Client Forward Move
       */
      "increment the server move's fromIndex and decrement the client move's fromIndex, when the server move is overlapped by the client move" in {
        val s = ArrayMoveOperation(Path, false, 6, 4)
        val c = ArrayMoveOperation(Path, false, 3, 5)

        val (s1, c1) = ArrayMoveMoveTF.transform(s, c)

        s1 shouldBe ArrayMoveOperation(Path, false, 6, 3)
        c1 shouldBe ArrayMoveOperation(Path, false, 3, 6)
      }

      /**
       * A-MM-BF-7 - Server Backward Move Starts Client Forward Move
       */
      "increment the server move's fromIndex and toIndex and not transform the client move, when the server move starts the client move" in {
        val s = ArrayMoveOperation(Path, false, 5, 3)
        val c = ArrayMoveOperation(Path, false, 3, 6)

        val (s1, c1) = ArrayMoveMoveTF.transform(s, c)

        s1 shouldBe ArrayMoveOperation(Path, false, 4, 3)
        c1 shouldBe ArrayMoveOperation(Path, false, 4, 6)
      }

      /**
       * A-MM-BF-8 - Server Backward Move Started By Client Forward Move
       */
      "increment the server move's fromIndex and decrement the client move's fromInex, when the server move is started by the client move" in {
        val s = ArrayMoveOperation(Path, false, 6, 3)
        val c = ArrayMoveOperation(Path, false, 3, 5)

        val (s1, c1) = ArrayMoveMoveTF.transform(s, c)

        s1 shouldBe ArrayMoveOperation(Path, false, 6, 3)
        c1 shouldBe ArrayMoveOperation(Path, false, 4, 6)
      }

      /**
       * A-MM-BF-9 - Server Backward Move Contains Client Forward Move
       */
      "not transform the server move and decrement the client move's fromIndex and toIndex, when the server move contains the client move" in {
        val s = ArrayMoveOperation(Path, false, 6, 3)
        val c = ArrayMoveOperation(Path, false, 4, 5)

        val (s1, c1) = ArrayMoveMoveTF.transform(s, c)

        s1 shouldBe s
        c1 shouldBe ArrayMoveOperation(Path, false, 5, 6)
      }

      /**
       * A-MM-BF-10 - Server Backward Move Contained By Client Forward Move
       */
      "increment the server move's fromIndex and toIndex and not transform the client move, when the server move is contained by the client move" in {
        val s = ArrayMoveOperation(Path, false, 5, 4)
        val c = ArrayMoveOperation(Path, false, 3, 6)

        val (s1, c1) = ArrayMoveMoveTF.transform(s, c)

        s1 shouldBe ArrayMoveOperation(Path, false, 4, 3)
        c1 shouldBe c
      }

      /**
       * A-MM-BF-11 - Server Backward Move Finishes Client Forward Move
       */
      "decrement the server move's fromIndex and the client move's fromIndex, when the server move finishes the client move" in {
        val s = ArrayMoveOperation(Path, false, 6, 4)
        val c = ArrayMoveOperation(Path, false, 2, 6)

        val (s1, c1) = ArrayMoveMoveTF.transform(s, c)

        s1 shouldBe ArrayMoveOperation(Path, false, 5, 3)
        c1 shouldBe c
      }

      /**
       * A-MM-BF-12 - Server Backward Move Finished By Client Forward Move
       */
      "not transform the server move and decrement the client move's fromIndex and toIndex, when the server move finishes the client move" in {
        val s = ArrayMoveOperation(Path, false, 6, 2)
        val c = ArrayMoveOperation(Path, false, 4, 6)

        val (s1, c1) = ArrayMoveMoveTF.transform(s, c)

        s1 shouldBe ArrayMoveOperation(Path, false, 5, 2)
        c1 shouldBe ArrayMoveOperation(Path, false, 5, 6)
      }

      /**
       * A-MM-BF-13 - Server Backward Move Equal To Client Forward Move
       */
      "increment the server move's fromIndex and decrement the client move's fromIndex, when the server move is equal to the client move" in {
        val s = ArrayMoveOperation(Path, false, 6, 2)
        val c = ArrayMoveOperation(Path, false, 2, 6)

        val (s1, c1) = ArrayMoveMoveTF.transform(s, c)

        s1 shouldBe ArrayMoveOperation(Path, false, 5, 2)
        c1 shouldBe ArrayMoveOperation(Path, false, 3, 6)
      }
    }

    "tranforming a server backward move against a client backward move" must {
      /**
       * A-MM-BB-1 - Server Backward Move Precedes Client Backward Move
       */
      "not transform either operation when the server's move preceeds the client's move" in {
        val s = ArrayMoveOperation(Path, false, 4, 2)
        val c = ArrayMoveOperation(Path, false, 7, 5)

        val (s1, c1) = ArrayMoveMoveTF.transform(s, c)

        s1 shouldBe s
        c1 shouldBe c
      }

      /**
       * A-MM-BB-2 - Server Backward Move Preceded By Client Backward Move
       */
      "not transform either operation when the server's move is preceded by the client's move" in {
        val s = ArrayMoveOperation(Path, false, 7, 5)
        val c = ArrayMoveOperation(Path, false, 4, 2)

        val (s1, c1) = ArrayMoveMoveTF.transform(s, c)

        s1 shouldBe s
        c1 shouldBe c
      }

      /**
       * A-MM-BB-3 - Server Backward Move Meets Client Backward Move
       */
      "decrement the server move's toIndex and decrement the client move's fromIndex when the server move meets the client move" in {
        val s = ArrayMoveOperation(Path, false, 5, 3)
        val c = ArrayMoveOperation(Path, false, 7, 5)

        val (s1, c1) = ArrayMoveMoveTF.transform(s, c)

        s1 shouldBe ArrayMoveOperation(Path, false, 6, 3)
        c1 shouldBe ArrayMoveOperation(Path, false, 7, 6)
      }

      /**
       * A-MM-BB-4 - Server Backward Move Met By Client Backward Move
       */
      "set the server move's fromIndex to the client move's toIndex and noOp the client move, when the server move is met by the client move" in {
        val s = ArrayMoveOperation(Path, false, 7, 5)
        val c = ArrayMoveOperation(Path, false, 5, 3)

        val (s1, c1) = ArrayMoveMoveTF.transform(s, c)

        s1 shouldBe ArrayMoveOperation(Path, false, 7, 6)
        c1 shouldBe ArrayMoveOperation(Path, false, 6, 3)
      }

      /**
       * A-MM-BB-5 - Server Backward Move Overlaps Client Backward Move
       */
      "increment the server move's toIndex and decrement the client move's toIndex, when the server move overlaps the client move" in {
        val s = ArrayMoveOperation(Path, false, 5, 3)
        val c = ArrayMoveOperation(Path, false, 6, 4)

        val (s1, c1) = ArrayMoveMoveTF.transform(s, c)

        s1 shouldBe ArrayMoveOperation(Path, false, 6, 3)
        c1 shouldBe ArrayMoveOperation(Path, false, 6, 5)
      }

      /**
       * A-MM-BB-6 - Server Backward Move Overlapped By Client Backward Move
       */
      "increment the server move's fromIndex and decrement the client move's fromIndex, when the server move is overlapped by the client move" in {
        val s = ArrayMoveOperation(Path, false, 6, 4)
        val c = ArrayMoveOperation(Path, false, 5, 3)

        val (s1, c1) = ArrayMoveMoveTF.transform(s, c)

        s1 shouldBe ArrayMoveOperation(Path, false, 6, 5)
        c1 shouldBe ArrayMoveOperation(Path, false, 6, 3)
      }

      /**
       * A-MM-BB-7 - Server Backward Move Starts Client Backward Move
       */
      "increment the server move's fromIndex and toIndex and not transform the client move, when the server move starts the client move" in {
        val s = ArrayMoveOperation(Path, false, 5, 3)
        val c = ArrayMoveOperation(Path, false, 6, 3)

        val (s1, c1) = ArrayMoveMoveTF.transform(s, c)

        s1 shouldBe ArrayMoveOperation(Path, false, 6, 4)
        c1 shouldBe ArrayMoveOperation(Path, false, 6, 3)
      }

      /**
       * A-MM-BB-8 - Server Backward Move Started By Client Backward Move
       */
      "increment the server move's fromIndex and decrement the client move's fromInex, when the server move is started by the client move" in {
        val s = ArrayMoveOperation(Path, false, 6, 3)
        val c = ArrayMoveOperation(Path, false, 5, 3)

        val (s1, c1) = ArrayMoveMoveTF.transform(s, c)

        s1 shouldBe ArrayMoveOperation(Path, false, 6, 3)
        c1 shouldBe ArrayMoveOperation(Path, false, 6, 4)
      }

      /**
       * A-MM-BB-9 - Server Backward Move Contains Client Backward Move
       */
      "not transform the server move and decrement the client move's fromIndex and toIndex, when the server move contains the client move" in {
        val s = ArrayMoveOperation(Path, false, 6, 3)
        val c = ArrayMoveOperation(Path, false, 5, 4)

        val (s1, c1) = ArrayMoveMoveTF.transform(s, c)

        s1 shouldBe s
        c1 shouldBe ArrayMoveOperation(Path, false, 6, 5)
      }

      /**
       * A-MM-BB-10 - Server Backward Move Contained By Client Backward Move
       */
      "increment the server move's fromIndex and toIndex and not transform the client move, when the server move is contained by the client move" in {
        val s = ArrayMoveOperation(Path, false, 5, 4)
        val c = ArrayMoveOperation(Path, false, 6, 3)

        val (s1, c1) = ArrayMoveMoveTF.transform(s, c)

        s1 shouldBe ArrayMoveOperation(Path, false, 6, 5)
        c1 shouldBe c
      }

      /**
       * A-MM-BF-11 - Server Backward Move Finishes Client Backward Move
       */
      "decrement the server move's fromIndex and the client move's fromIndex, when the server move finishes the client move" in {
        val s = ArrayMoveOperation(Path, false, 6, 4)
        val c = ArrayMoveOperation(Path, false, 6, 2)

        val (s1, c1) = ArrayMoveMoveTF.transform(s, c)

        s1 shouldBe ArrayMoveOperation(Path, false, 2, 4)
        c1 shouldBe ArrayMoveOperation(Path, true, 6, 2)
      }

      /**
       * A-MM-BB-12 - Server Backward Move Finished By Client Backward Move
       */
      "not transform the server move and decrement the client move's fromIndex and toIndex, when the server move finishes the client move" in {
        val s = ArrayMoveOperation(Path, false, 6, 2)
        val c = ArrayMoveOperation(Path, false, 6, 4)

        val (s1, c1) = ArrayMoveMoveTF.transform(s, c)

        s1 shouldBe ArrayMoveOperation(Path, false, 4, 2)
        c1 shouldBe ArrayMoveOperation(Path, true, 6, 4)
      }

      /**
       * A-MM-BB-13 - Server Backward Move Equal To Client Backward Move
       */
      "increment the server move's fromIndex and decrement the client move's fromIndex, when the server move is equal to the client move" in {
        val s = ArrayMoveOperation(Path, false, 6, 2)
        val c = ArrayMoveOperation(Path, false, 6, 2)

        val (s1, c1) = ArrayMoveMoveTF.transform(s, c)

        s1 shouldBe ArrayMoveOperation(Path, true, 6, 2)
        c1 shouldBe ArrayMoveOperation(Path, true, 6, 2)
      }
    }
    
    "tranforming a server identity move against a client backward move" must {

      /**
       * A-MM-BI-1
       */
      "not transform either operation when the server's move is before the client's move" in {
        val s = ArrayMoveOperation(Path, false, 5, 3)
        val c = ArrayMoveOperation(Path, false, 2, 2)

        val (s1, c1) = ArrayMoveMoveTF.transform(s, c)

        s1 shouldBe s
        c1 shouldBe c
      }

      /**
       * A-MM-BI-2
       */
      "not transform the server move and shift the client's move one to the right, when the client move is at the start of the server's move" in {
        val s = ArrayMoveOperation(Path, false, 5, 3)
        val c = ArrayMoveOperation(Path, false, 3, 3)

        val (s1, c1) = ArrayMoveMoveTF.transform(s, c)

        s1 shouldBe s
        c1 shouldBe ArrayMoveOperation(Path, false, 4, 4)
      }

      /**
       * A-MM-BI-3
       */
      "not transform the server move and shift the client's move one to the right, when the client move is within the server's move" in {
        val s = ArrayMoveOperation(Path, false, 5, 3)
        val c = ArrayMoveOperation(Path, false, 4, 4)

        val (s1, c1) = ArrayMoveMoveTF.transform(s, c)

        s1 shouldBe s
        c1 shouldBe ArrayMoveOperation(Path, false, 5, 5)
      }

      /**
       * A-MM-BI-4
       */
      "not transform the server's move and move the client's move to the to index of the server's move, when the client move is at the end of the server's move" in {
        val s = ArrayMoveOperation(Path, false, 5, 3)
        val c = ArrayMoveOperation(Path, false, 5, 5)

        val (s1, c1) = ArrayMoveMoveTF.transform(s, c)

        s1 shouldBe s
        c1 shouldBe ArrayMoveOperation(Path, false, 3, 3)
      }

      /**
       * A-MM-BI-5
       */
      "not transform either operation, when the client's move is after the server's move" in {
        val s = ArrayMoveOperation(Path, false, 5, 3)
        val c = ArrayMoveOperation(Path, false, 6, 6)

        val (s1, c1) = ArrayMoveMoveTF.transform(s, c)

        s1 shouldBe s
        c1 shouldBe c
      }
    }

    "tranforming a server identity move against a client forward move" must {

      /**
       * A-MM-IF-1
       */
      "not transform either operation when the client's move is before the server's move" in {
        val s = ArrayMoveOperation(Path, false, 2, 2)
        val c = ArrayMoveOperation(Path, false, 3, 5)

        val (s1, c1) = ArrayMoveMoveTF.transform(s, c)

        s1 shouldBe s
        c1 shouldBe c
      }

      /**
       * A-MM-IF-2
       */
      "not transform the client move and move the server's move to the toIndex of the clients move, when the server move is at the start of the client's move" in {
        val s = ArrayMoveOperation(Path, false, 3, 3)
        val c = ArrayMoveOperation(Path, false, 3, 5)

        val (s1, c1) = ArrayMoveMoveTF.transform(s, c)

        s1 shouldBe ArrayMoveOperation(Path, false, 5, 5)
        c1 shouldBe c
      }

      /**
       * A-MM-IF-3
       */
      "not transform the client operation and shift the server's move one to the left, when the server move is within the client's move" in {
        val s = ArrayMoveOperation(Path, false, 4, 4)
        val c = ArrayMoveOperation(Path, false, 3, 5)

        val (s1, c1) = ArrayMoveMoveTF.transform(s, c)

        s1 shouldBe ArrayMoveOperation(Path, false, 3, 3)
        c1 shouldBe c
      }

      /**
       * A-MM-IF-4
       */
      "not transform the client operation and shift the server's move one to the left, when the server move is at the end of the client's move" in {
        val s = ArrayMoveOperation(Path, false, 5, 5)
        val c = ArrayMoveOperation(Path, false, 3, 5)

        val (s1, c1) = ArrayMoveMoveTF.transform(s, c)

        s1 shouldBe ArrayMoveOperation(Path, false, 4, 4)
        c1 shouldBe c
      }

      /**
       * A-MM-IF-5
       */
      "not transform either operation, when the server's move is after the client's move" in {
        val s = ArrayMoveOperation(Path, false, 6, 6)
        val c = ArrayMoveOperation(Path, false, 3, 5)

        val (s1, c1) = ArrayMoveMoveTF.transform(s, c)

        s1 shouldBe s
        c1 shouldBe c
      }
    }

    "tranforming a server identity move against a client backward move" must {

      /**
       * A-MM-IB-1
       */
      "not transform either operation when the client's move is before the server's move" in {
        val s = ArrayMoveOperation(Path, false, 2, 2)
        val c = ArrayMoveOperation(Path, false, 5, 3)

        val (s1, c1) = ArrayMoveMoveTF.transform(s, c)

        s1 shouldBe s
        c1 shouldBe c
      }

      /**
       * A-MM-IB-2
       */
      "not transform the client move and move the server's shift the server's move one to the right, when the server move is at the start of the client's move" in {
        val s = ArrayMoveOperation(Path, false, 3, 3)
        val c = ArrayMoveOperation(Path, false, 5, 3)

        val (s1, c1) = ArrayMoveMoveTF.transform(s, c)

        s1 shouldBe ArrayMoveOperation(Path, false, 4, 4)
        c1 shouldBe c
      }

      /**
       * A-MM-IB-3
       */
      "not transform the client move and move the server's shift the server's move one to the right, when the server move is within the client's move" in {
        val s = ArrayMoveOperation(Path, false, 4, 4)
        val c = ArrayMoveOperation(Path, false, 5, 3)

        val (s1, c1) = ArrayMoveMoveTF.transform(s, c)

        s1 shouldBe ArrayMoveOperation(Path, false, 5, 5)
        c1 shouldBe c
      }

      /**
       * A-MM-IB-4
       */
      "not transform the client operation and move the server's move to the to index of the client's move, when the server move is at the end of the client's move" in {
        val s = ArrayMoveOperation(Path, false, 5, 5)
        val c = ArrayMoveOperation(Path, false, 5, 3)

        val (s1, c1) = ArrayMoveMoveTF.transform(s, c)

        s1 shouldBe ArrayMoveOperation(Path, false, 3, 3)
        c1 shouldBe c
      }

      /**
       * A-MM-IB-5
       */
      "not transform either operation, when the server's move is after the client's move" in {
        val s = ArrayMoveOperation(Path, false, 6, 6)
        val c = ArrayMoveOperation(Path, false, 5, 3)

        val (s1, c1) = ArrayMoveMoveTF.transform(s, c)

        s1 shouldBe s
        c1 shouldBe c
      }
    }

    "tranforming a server identity move against a client identity move" must {

      /**
       * A-MM-II-1
       */
      "not transform either operation" in {
        val s = ArrayMoveOperation(Path, false, 2, 2)
        val c = ArrayMoveOperation(Path, false, 3, 3)

        val (s1, c1) = ArrayMoveMoveTF.transform(s, c)

        s1 shouldBe s
        c1 shouldBe c
      }
    }
  }
}
