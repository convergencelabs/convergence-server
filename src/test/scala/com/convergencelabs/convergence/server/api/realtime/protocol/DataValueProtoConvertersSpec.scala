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

package com.convergencelabs.convergence.server.api.realtime.protocol

import com.convergencelabs.convergence.proto

import com.convergencelabs.convergence.proto.model.DataValue
import com.convergencelabs.convergence.server.model.domain.model.BooleanValue
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec


class DataValueProtoConvertersSpec extends AnyWordSpec with Matchers {

  "An DataValueProtoConverter" when {
    "converting a BooleanValue to protocol buffers" must {
      "correctly convert the chat state" in {
        val booleanValue = BooleanValue("someId", value = true)
        val protoValue = proto.model.BooleanValue(booleanValue.id, booleanValue.value)
        DataValueProtoConverters.dataValueToProto(booleanValue) shouldBe DataValue().withBooleanValue(protoValue)
      }
    }
  }
}
