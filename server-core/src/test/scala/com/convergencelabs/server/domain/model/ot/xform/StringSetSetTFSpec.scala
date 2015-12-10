package com.convergencelabs.server.domain.model.ot

import org.scalatest.Finders
import org.scalatest.WordSpec
import org.scalatest.Matchers

// scalastyle:off magic.number multiple.string.literals
class StringSetSetTFSpec extends WordSpec with Matchers {

  val Path = List(1, 2)

  "A StringSetSetTF" when {

    "tranforming a server set against a client set " must {

      "noOp the client's set operation and not transform the server's set if the sets have different values" in {
        val s = StringSetOperation(Path, false, "y")
        val c = StringSetOperation(Path, false, "x")

        val (s1, c1) = StringSetSetTF.transform(s, c)

        s1 shouldBe s
        c1 shouldBe StringSetOperation(Path, true, "x")
      }

      "noOp the client's set operation and not transform the server's set if the sets have the same values" in {
        val s = StringSetOperation(Path, false, "x")
        val c = StringSetOperation(Path, false, "x")

        val (s1, c1) = StringSetSetTF.transform(s, c)

        s1 shouldBe StringSetOperation(Path, true, "x")
        c1 shouldBe StringSetOperation(Path, true, "x")
      }
    }
  }
}
