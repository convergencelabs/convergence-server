package com.convergencelabs.server.domain.model.ot

import org.scalatest.Finders
import org.scalatest.WordSpec

class StringInsertInsertTFSpec extends WordSpec {

  val Path = List(1, 2)
  val ClientVal = "x"
  val ServerVal = "y"

  "A StringInsertInsertTF" when {

    "tranforming two insert operations " must {

      "increment the client's index if both operations target the same index" in {
        val s = StringInsertOperation(Path, false, 2, ServerVal)
        val c = StringInsertOperation(Path, false, 2, ClientVal)

        val (s1, c1) = StringInsertInsertTF.transform(s, c)

        assert(s1 == s)
        assert(c1 == StringInsertOperation(Path, false, 3, ClientVal))
      }

      "increment the client's index if the server's index is before the client's" in {
        val s = StringInsertOperation(Path, false, 2, ServerVal)
        val c = StringInsertOperation(Path, false, 3, ClientVal)

        val (s1, c1) = StringInsertInsertTF.transform(s, c)

        assert(s1 == s)
        assert(c1 == StringInsertOperation(Path, false, 4, ClientVal))
      }

      "increment the server's index if the server's index is after the client's" in {
        val s = StringInsertOperation(Path, false, 3, ServerVal)
        val c = StringInsertOperation(Path, false, 2, ClientVal)

        val (s1, c1) = StringInsertInsertTF.transform(s, c)

        assert(s1 == StringInsertOperation(Path, false, 4, ServerVal))
        assert(c1 == c)
      }
    }
  }
}