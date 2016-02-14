package com.convergencelabs.server.domain.model.ot

import org.scalatest.Finders
import org.scalatest.WordSpec

class StringRemoveSetTFSpec extends WordSpec {

  val Path = List(1, 2)
  val ClientVal = "x"
  val ServerVal = "y"

  "A StringRemoveSetTF" when {

    "tranforming a server remove against a client set " must {

      "noOp the server's remove operation and not transform the client's set" in {
        val s = StringRemoveOperation(Path, false, 2, ServerVal)
        val c = StringSetOperation(Path, false, ClientVal)

        val (s1, c1) = StringRemoveSetTF.transform(s, c)

        assert(s1 == StringRemoveOperation(Path, true, 2, ServerVal))
        assert(c1 == c)
      }
    }
  }
}
