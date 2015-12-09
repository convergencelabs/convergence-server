package com.convergencelabs.server.domain.model.ot

import org.scalatest.Finders
import org.scalatest.WordSpec

class StringInsertRemoveTFSpec extends WordSpec {

  val Path = List(1, 2)
  val ClientVal = "x"
  val ServerVal = "y"

  "A StringInsertRemoveTF" when {

    "tranforming a server insert against a client remove " must {

      "increment the client index if both operations target the same index" in {
        val s = StringInsertOperation(Path, false, 2, ServerVal)
        val c = StringRemoveOperation(Path, false, 2, ClientVal)

        val (s1, c1) = StringInsertRemoveTF.transform(s, c)

        assert(s1 == s)
        assert(c1 == StringRemoveOperation(Path, false, 3, ClientVal))
      }

      "increment the client index if the server's index is before the client's" in {
        val s = StringInsertOperation(Path, false, 2, ServerVal)
        val c = StringRemoveOperation(Path, false, 3, ClientVal)

        val (s1, c1) = StringInsertRemoveTF.transform(s, c)

        assert(s1 == s)
        assert(c1 == StringRemoveOperation(Path, false, 4, ClientVal))
      }

      "decrement the server index if the server's index is after the client's" in {
        val s = StringInsertOperation(Path, false, 3, ServerVal)
        val c = StringRemoveOperation(Path, false, 2, ClientVal)

        val (s1, c1) = StringInsertRemoveTF.transform(s, c)

        assert(s1 == StringInsertOperation(Path, false, 2, ServerVal))
        assert(c1 == c)
      }
    }
  }
}
