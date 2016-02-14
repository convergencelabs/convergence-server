package com.convergencelabs.server.domain.model.ot

import org.json4s.JsonAST.JArray
import org.json4s.JsonAST.JString
import org.scalatest.Finders
import org.scalatest.WordSpec
import org.scalatest.Matchers

// scalastyle:off magic.number
class ArrayRemoveSetTFSpec extends WordSpec with Matchers {

  val Path = List(1, 2)
  val X = JArray(List(JString("x")))

  "A ArrayRemoveSetTF" when {

    "transforming a server's remove against a client's set" must {

      /**
       * A-RS-1
       */
      "noOp the server's operation and not transform the client's" in {
        val s = ArrayRemoveOperation(Path, false, 4)
        val c = ArraySetOperation(Path, false, X)

        val (s1, c1) = ArrayRemoveSetTF.transform(s, c)

        s1 shouldBe ArrayRemoveOperation(Path, true, 4)
        c1 shouldBe c
      }
    }
  }
}
