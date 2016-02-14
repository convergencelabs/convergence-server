package com.convergencelabs.server.domain.model.ot

import org.scalatest.Finders
import org.scalatest.WordSpec
import org.scalatest.Matchers

// scalastyle:off magic.number
class StringInsertRemoveTFSpec extends WordSpec with Matchers {

  val Path = List(1, 2)
  val ClientVal = "x"
  val ServerVal = "y"

  "A StringInsertRemoveTF" when {

    "tranforming a server insert against a client remove " must {

      "increment the client index if both operations target the same index" in {
        val s = StringInsertOperation(Path, false, 2, ServerVal)
        val c = StringRemoveOperation(Path, false, 2, ClientVal)

        val (s1, c1) = StringInsertRemoveTF.transform(s, c)

        s1 shouldBe s
        c1 shouldBe StringRemoveOperation(Path, false, 3, ClientVal)
      }

      "increment the client index if the server's index is before the client's" in {
        val s = StringInsertOperation(Path, false, 2, ServerVal)
        val c = StringRemoveOperation(Path, false, 3, ClientVal)

        val (s1, c1) = StringInsertRemoveTF.transform(s, c)

        s1 shouldBe s
        c1 shouldBe StringRemoveOperation(Path, false, 4, ClientVal)
      }

      "decrement the server index if the server's index is after the client's" in {
        val s = StringInsertOperation(Path, false, 3, ServerVal)
        val c = StringRemoveOperation(Path, false, 2, ClientVal)

        val (s1, c1) = StringInsertRemoveTF.transform(s, c)

        s1 shouldBe StringInsertOperation(Path, false, 2, ServerVal)
        c1 shouldBe c
      }
    }
  }
}
