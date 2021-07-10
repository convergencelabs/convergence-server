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

package com.convergencelabs.convergence.server.backend.services.domain.model.ot.xform

import com.convergencelabs.convergence.server.backend.services.domain.model.ot._
import org.mockito.Matchers.anyObject
import org.mockito.Mockito
import org.mockito.Mockito.{spy, times, verify, when}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar


// scalastyle:off multiple.string.literals
class OperationTransformerSpec
    extends AnyWordSpec
    with Matchers
    with MockitoSugar {

  val valueId = "testId"

  "A OperationTransformer" when {

    "transforming identical path operations of the same type" must {

      "transform two discrete operations in the proper order" in new WithIdentityTransform {
        val s = StringSpliceOperation( valueId, false, 1, 0, "s")
        val c = StringSpliceOperation( valueId, false, 1, 0, "c")
        transformer.transform(s, c)
        verify(otfSpy, times(1)).transform(s, c)
      }

      "transform a server compound op against a client discrete op" in new WithIdentityTransform {
        val s1 = StringSpliceOperation( valueId, false, 1, 0, "s1")
        val s2 = StringSpliceOperation( valueId, false, 1, 0, "s2")
        val s = CompoundOperation(List(s1, s2))

        val c = StringSpliceOperation( valueId, false, 1, 0, "c")
        transformer.transform(s, c)

        val ordered = Mockito.inOrder(otfSpy)

        ordered.verify(otfSpy, times(1)).transform(s1, c)
        ordered.verify(otfSpy, times(1)).transform(s2, c)
      }

      "transform a client compound op against a server discrete op" in new WithIdentityTransform {
        val s = StringSpliceOperation( valueId, false, 1, 0, "s")

        val c1 = StringSpliceOperation( valueId, false, 1, 0, "c1")
        val c2 = StringSpliceOperation( valueId, false, 1, 0, "c2")
        val c = CompoundOperation(List(c1, c2))
        transformer.transform(s, c)

        val ordered = Mockito.inOrder(otfSpy)

        ordered.verify(otfSpy, times(1)).transform(s, c1)
        ordered.verify(otfSpy, times(1)).transform(s, c2)
      }

      "transform a server compound op against a server compound  op" in new WithIdentityTransform {
        val s1 = StringSpliceOperation( valueId, false, 1, 0, "s1")
        val s2 = StringSpliceOperation( valueId, false, 1, 0, "s2")
        val s = CompoundOperation(List(s1, s2))

        val c1 = StringSpliceOperation( valueId, false, 1, 0, "c1")
        val c2 = StringSpliceOperation( valueId, false, 1, 0, "c2")
        val c = CompoundOperation(List(c1, c2))
        transformer.transform(s, c)

        val ordered = Mockito.inOrder(otfSpy)

        ordered.verify(otfSpy, times(1)).transform(s1, c1)
        ordered.verify(otfSpy, times(1)).transform(s1, c2)
        ordered.verify(otfSpy, times(1)).transform(s2, c1)
        ordered.verify(otfSpy, times(1)).transform(s2, c2)
      }

      "perform no transformation if the server is a noOp" in new WithIdentityTransform {
        val s = StringSpliceOperation(valueId, true, 1, 0, "s")
        val c = StringSpliceOperation( valueId, false, 1, 0, "c")
        val (sx, cx) = transformer.transform(s, c)
        sx shouldBe s
        cx shouldBe c

        verify(otfSpy, times(0)).transform(s, c)
      }

      "perform no transformation if the client is a noOp" in new WithIdentityTransform {
        val s = StringSpliceOperation( valueId, false, 1, 0, "s")
        val c = StringSpliceOperation(valueId, true, 1, 0, "c")
        val (sx, cx) = transformer.transform(s, c)
        sx shouldBe s
        cx shouldBe c

        verify(otfSpy, times(0)).transform(s, c)
      }

      "perform no transformation if both ops are noOps" in new WithIdentityTransform {
        val s = StringSpliceOperation(valueId, true, 1, 0, "s")
        val c = StringSpliceOperation(valueId, true, 1, 0, "c")
        val (sx, cx) = transformer.transform(s, c)
        sx shouldBe s
        cx shouldBe c

        verify(otfSpy, times(0)).transform(s, c)
      }
    }

    "transforming unrelated operations" must {
      "not transform the operations" in new WithIdentityTransform {
        val s = StringSpliceOperation("x", false, 1, 0, "s")
        val c = StringSpliceOperation("y", false, 1, 0, "c")
        val (sx, cx) = transformer.transform(s, c)
        sx shouldBe s
        cx shouldBe c

        verify(otfSpy, times(0)).transform(s, c)
      }
    }

    "handinling a None from the transformation function registry" must {
      "throw an exception when getting None for an operation transformation function" in new TestFixture {
        when(tfRegistry.getOperationTransformationFunction(anyObject[DiscreteOperation](), anyObject[DiscreteOperation]())).thenReturn(None)
        intercept[IllegalArgumentException] {
          transformer.transform(
            StringSpliceOperation( valueId, false, 1, 0, "s"),
            StringSpliceOperation( valueId, false, 1, 0, "s"))
        }
      }
    }
  }

  trait TestFixture {
    val parentPath = List("parent")
    val childPath = parentPath :+ "child"

    val tfRegistry = mock[TransformationFunctionRegistry]
    val otfSpy = spy(new IdentityTransform())
    val transformer = new OperationTransformer(tfRegistry)
  }

  trait WithIdentityTransform extends TestFixture {
    when(tfRegistry.getOperationTransformationFunction(anyObject[DiscreteOperation](), anyObject[DiscreteOperation]())).thenReturn(Some(otfSpy))
  }

  class IdentityTransform extends OperationTransformationFunction[DiscreteOperation, DiscreteOperation] {
    def transform(s: DiscreteOperation, c: DiscreteOperation): (DiscreteOperation, DiscreteOperation) = (s, c)
  }
}
