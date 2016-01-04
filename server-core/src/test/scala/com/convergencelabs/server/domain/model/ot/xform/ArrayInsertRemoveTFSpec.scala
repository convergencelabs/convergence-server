package com.convergencelabs.server.domain.model.ot

import org.json4s.JsonAST.JString
import org.json4s.JsonDSL.string2jvalue
import org.scalatest.Finders
import org.scalatest.WordSpec
import org.scalatest.Matchers

// scalastyle:off magic.number multiple.string.literals
class ArrayInsertRemoveTFSpec
    extends WordSpec
    with Matchers {

  val Path = List(1, 2)

  "A ArrayInsertRemoveTF" when {

    "tranforming a server insert against a client remove " must {

      /**
       * A-IR-1
       */
      "increment the client remove's index, when the server insert's index is less than the client remove's index" in {
        val s = ArrayInsertOperation(Path, false, 4, "X")
        val c = ArrayRemoveOperation(Path, false, 5)

        val (s1, c1) = ArrayInsertRemoveTF.transform(s, c)

        s1 shouldBe s
        c1 shouldBe ArrayRemoveOperation(Path, false, 6)
      }

      /**
       * A-IR-2
       */
      "increment the client remove's index, when the server insert's index is equal to the client remove's index" in {
        val s = ArrayInsertOperation(Path, false, 4, "X")
        val c = ArrayRemoveOperation(Path, false, 4)

        val (s1, c1) = ArrayInsertRemoveTF.transform(s, c)

        s1 shouldBe s
        c1 shouldBe ArrayRemoveOperation(Path, false, 5)
      }

      /**
       * A-IR-3
       */
      "decrement the server insert's index if the server insert's index is greater than the client remove's index" in {
        val s = ArrayInsertOperation(Path, false, 5, "X")
        val c = ArrayRemoveOperation(Path, false, 4)

        val (s1, c1) = ArrayInsertRemoveTF.transform(s, c)

        s1 shouldBe ArrayInsertOperation(Path, false, 4, "X")
        c1 shouldBe c
      }
    }
  }
}
