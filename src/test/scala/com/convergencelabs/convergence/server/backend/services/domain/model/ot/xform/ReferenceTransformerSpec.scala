/*
 * Copyright (c) 2021 - Convergence Labs, Inc.
 *
 * This file is part of the Convergence Server, which is released under
 * the terms of the GNU General Public License version 3 (GPLv3). A copy
 * of the GPLv3 should have been provided along with this file, typically
 * located in the "LICENSE" file, which is part of this source code package.
 * Alternatively, see <https://www.gnu.org/licenses/gpl-3.0.html> for the
 * full text of the GPLv3 license, if it was not provided.
 */

package com.convergencelabs.convergence.server.backend.services.domain.model.ot.xform

import com.convergencelabs.convergence.server.backend.services.domain.model.ot._
import com.convergencelabs.convergence.server.model.domain.model.{IndexReferenceValues, ModelReferenceValues}
import org.mockito.Matchers.anyObject
import org.mockito.Mockito
import org.mockito.Mockito.{spy, times, verify, when}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar


// scalastyle:off multiple.string.literals
class ReferenceTransformerSpec
  extends AnyWordSpec
    with Matchers
    with MockitoSugar {

  val valueId = "testId"
  val otherValueId = "otherId"

  "A ReferenceTransformer" when {

    "transforming reference values against an operation" must {

      "not transform references against operations targeted at other elements" in new WithIdentityTransform {
        val s: StringSpliceOperation = StringSpliceOperation(valueId, noOp = false, 1, 0, "s")
        val values: IndexReferenceValues = IndexReferenceValues(List(10, 20))
        transformer.transform(s, otherValueId, values)
        verify(otfSpy, times(0)).transform(s, values)
      }

      "transform references against operations that target the same element" in new WithIdentityTransform {
        val s: StringSpliceOperation = StringSpliceOperation(valueId, noOp = false, 1, 0, "s")
        val values: IndexReferenceValues = IndexReferenceValues(List(10, 20))
        transformer.transform(s, valueId, values)
        verify(otfSpy, times(1)).transform(s, values)
      }
    }
  }

  trait TestFixture {
    val tfRegistry: TransformationFunctionRegistry = mock[TransformationFunctionRegistry]
    val otfSpy: IdentityTransform = spy(new IdentityTransform())
    val transformer = new ReferenceTransformer(tfRegistry)
  }

  trait WithIdentityTransform extends TestFixture {
    when(tfRegistry.getReferenceTransformationFunction(anyObject[DiscreteOperation](), anyObject[ModelReferenceValues]())).thenReturn(Some(otfSpy))
  }

  class IdentityTransform extends ReferenceTransformationFunction[DiscreteOperation, ModelReferenceValues] {
    override def transform(serverOp: DiscreteOperation, values: ModelReferenceValues): Option[ModelReferenceValues] = Some(values)
  }
}
