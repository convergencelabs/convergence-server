package com.convergencelabs.server.domain.model.ot

import org.scalatest.Finders
import org.scalatest.WordSpec

class StringSetRemoveTFSpec extends WordSpec {

  val Path = List(1, 2)
  val ClientVal = "x"
  val ServerVal = "y"

  "A StringSetRemoveTF" when {

    "tranforming a server set against a client insert " must {

      "noOp the client's sinert operation and not transform the server's set" in {
        val s = StringSetOperation(Path, false, ServerVal)
        val c = StringRemoveOperation(Path, false, 2, ClientVal)

        val (s1, c1) = StringSetRemoveTF.transform(s, c)

        assert(s1 == s)
        assert(c1 == StringRemoveOperation(Path, true, 2, ClientVal))
      }
    }
  }
}
