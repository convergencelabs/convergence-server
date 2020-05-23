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

package com.convergencelabs.convergence.server.api.realtime

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import com.convergencelabs.convergence.server.domain.model.data.DoubleValue
import com.convergencelabs.convergence.server.domain.model.ot.ArrayInsertOperation
import com.convergencelabs.convergence.server.domain.model.ot.ArrayMoveOperation
import com.convergencelabs.convergence.server.domain.model.ot.ArrayRemoveOperation
import com.convergencelabs.convergence.server.domain.model.ot.ArrayReplaceOperation
import com.convergencelabs.convergence.server.domain.model.ot.ArraySetOperation
import com.convergencelabs.convergence.server.domain.model.ot.BooleanSetOperation
import com.convergencelabs.convergence.server.domain.model.ot.CompoundOperation
import com.convergencelabs.convergence.server.domain.model.ot.NumberAddOperation
import com.convergencelabs.convergence.server.domain.model.ot.NumberSetOperation
import com.convergencelabs.convergence.server.domain.model.ot.ObjectAddPropertyOperation
import com.convergencelabs.convergence.server.domain.model.ot.ObjectRemovePropertyOperation
import com.convergencelabs.convergence.server.domain.model.ot.ObjectSetOperation
import com.convergencelabs.convergence.server.domain.model.ot.ObjectSetPropertyOperation
import com.convergencelabs.convergence.server.domain.model.ot.StringInsertOperation
import com.convergencelabs.convergence.server.domain.model.ot.StringRemoveOperation
import com.convergencelabs.convergence.server.domain.model.ot.StringSetOperation

// scalastyle:off magic.number
class OperationMapperSpec extends AnyWordSpec with Matchers {

  val X = "X"

  val Id = "testId"
  val NoOp = true
  val Value = DoubleValue("vid", 2)
  val Prop = "prop"

  val operations = List(
    ObjectSetPropertyOperation(Id, NoOp, Prop, Value),
    ObjectAddPropertyOperation(Id, NoOp, Prop, Value),
    ObjectRemovePropertyOperation(Id, NoOp, Prop),
    ObjectSetOperation(Id, NoOp, Map("p" -> Value)),

    ArrayInsertOperation(Id, NoOp, 1, Value),
    ArrayRemoveOperation(Id, NoOp, 1),
    ArrayReplaceOperation(Id, NoOp, 1, Value),
    ArrayMoveOperation(Id, NoOp, 1, 2),
    ArraySetOperation(Id, NoOp, List(Value)),

    StringInsertOperation(Id, NoOp, 1, X),
    StringRemoveOperation(Id, NoOp, 1, X),
    StringSetOperation(Id, NoOp, X),

    NumberSetOperation(Id, NoOp, 3),
    NumberAddOperation(Id, NoOp, 4),

    BooleanSetOperation(Id, NoOp, true),

    CompoundOperation(List(NumberSetOperation(Id, NoOp, 3))))

  "An OperationMapper" when {
    "mapping an unmapping operations" must {
      "correctly map and unmap operations" in {
        operations.foreach { op =>
          val data = OperationMapper.mapOutgoing(op)
          val reverted = OperationMapper.mapIncoming(data)
          reverted shouldBe op
        }
      }
    }
  }
}
