package com.convergencelabs.server.domain.model.ot

import org.json4s.JsonAST.JString
import org.json4s.JsonDSL.string2jvalue
import org.scalatest.Finders
import org.scalatest.WordSpec
import org.scalatest.Matchers

// scalastyle:off magic.number multiple.string.literals
class ArrayInsertReplaceTFSpec
    extends WordSpec
    with Matchers {

  val Path = List(1, 2)

  "A ArrayInsertReplaceTF" when {

    "tranforming a server insert against a client replace" must {

      /**
       * A-IP-1
       */
      "increment the client's index if both operations target the same index" in {
        val s = ArrayInsertOperation(Path, false, 4, "X")
        val c = ArrayReplaceOperation(Path, false, 5, "Y")

        val (s1, c1) = ArrayInsertReplaceTF.transform(s, c)

        s1 shouldBe s
        c1 shouldBe ArrayReplaceOperation(Path, false, 6, "Y")
      }

      /**
       * A-IP-2
       */
      "increment the client's index if the server's index is before the client's" in {
        val s = ArrayInsertOperation(Path, false, 4, "X")
        val c = ArrayReplaceOperation(Path, false, 4, "Y")

        val (s1, c1) = ArrayInsertReplaceTF.transform(s, c)

        s1 shouldBe s
        c1 shouldBe ArrayReplaceOperation(Path, false, 5, "Y")
      }

      /**
       * A-IP-3
       */
      "leave both operations the same if the client's replace is before the server's insert" in {
        val s = ArrayInsertOperation(Path, false, 5, "X")
        val c = ArrayReplaceOperation(Path, false, 4, "Y")

        val (s1, c1) = ArrayInsertReplaceTF.transform(s, c)

        s1 shouldBe s
        c1 shouldBe c
      }
    }
  }
}
