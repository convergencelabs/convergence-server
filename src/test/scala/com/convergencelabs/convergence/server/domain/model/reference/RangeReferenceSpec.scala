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

package com.convergencelabs.convergence.server.domain.model.reference

import com.convergencelabs.convergence.server.domain.model.RealtimeString
import com.convergencelabs.convergence.server.domain.model.data.StringValue
import com.convergencelabs.convergence.server.domain.{DomainUserId, DomainUserSessionId}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class RangeReferenceSpec extends AnyWordSpec with Matchers {
  private[this] val session = DomainUserSessionId("session", DomainUserId.normal("user"))
  private[this] val key = "key"
  private[this] val stringValue = StringValue("id", "some string")
  private[this] val rts = new RealtimeString(stringValue, None, None)

  private[this] val val1 = RangeReference.Range(5, 15)
  private[this] val val2 = RangeReference.Range(10, 20)


  "A RangeReference" when {
    "when constructed" must {
      "have the correct values" in {
        val r = new RangeReference(rts, session, key, List(val1))
        r.target shouldBe rts
        r.session shouldBe session
        r.key shouldBe key
        r.get() shouldBe List(val1)
      }
    }

    "when setting values" must {
      "set the correct values" in {
        val r = new RangeReference(rts, session, key, List())
        r.set(List(val1))
        r.get() shouldBe List(val1)
      }
    }

    "handlePositionalInsert" must {
      "have the correct values" in {
        val r = new RangeReference(rts, session, key, List())
        r.set(List(val1, val2))

        r.handlePositionalInsert(6, 4)
        val xFormed = r.get()
        xFormed shouldBe List(RangeReference.Range(5, 19), RangeReference.Range(14, 24))
      }
    }
  }
}
