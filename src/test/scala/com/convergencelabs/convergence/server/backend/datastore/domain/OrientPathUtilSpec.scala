/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is part of the Convergence Server, which is released under
 * the terms of the GNU General Public License version 3 (GPLv3). A copy
 * of the GPLv3 should have been provided along with this file, typically
 * located in the "LICENSE" file, which is part of this source code package.
 * Alternatively, see <https://www.gnu.org/licenses/gpl-3.0.html> for the
 * full text of the GPLv3 license, if it was not provided.
 */

package com.convergencelabs.convergence.server.backend.datastore.domain

import com.convergencelabs.convergence.server.backend.datastore.domain.model.OrientPathUtil
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

class OrientPathUtilSpec
    extends AnyWordSpecLike
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
