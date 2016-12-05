package com.convergencelabs.server.datastore.domain

import org.scalatest.Finders
import org.scalatest.Matchers
import org.scalatest.WordSpecLike

class OrientPathUtilSpec
    extends WordSpecLike
    with Matchers {

  "A  OrientPathUtil" when {
    "calculating the orient path" must {
      "prepend 'data' and properly add all fields" in {
        val path = OrientPathUtil.toOrientPath(List(1, "foo", 2, "bar"))
        path shouldBe "data[1].foo[2].bar"
      }
    }

    "appending to a path" must {
      "" in {
        OrientPathUtil.appendToPath("data[1].foo[2].bar", "prop") shouldBe "data[1].foo[2].bar.prop"
      }
    }

  }
}
