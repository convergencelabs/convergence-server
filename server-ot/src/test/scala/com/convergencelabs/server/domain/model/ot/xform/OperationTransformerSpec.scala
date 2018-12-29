package com.convergencelabs.server.domain.model.ot

import org.scalatest.WordSpec
import org.scalatest.Matchers
import org.mockito.Mockito.mock
import org.mockito.Mockito.spy
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.when
import org.mockito.Mockito
import org.mockito.Matchers.any
import org.mockito.Matchers.anyObject
import org.scalatest.mockito.MockitoSugar

// scalastyle:off multiple.string.literals
class OperationTransformerSpec
    extends WordSpec
    with Matchers
    with MockitoSugar {

  val valueId = "testId"

  "A OperationTransformer" when {

    "transforming identical path operations of the same type" must {

      "transform two discrete operations in the proper order" in new WithIdnetityTransform {
        val s = StringInsertOperation(valueId, false, 1, "s")
        val c = StringInsertOperation(valueId, false, 1, "c")
        transformer.transform(s, c)
        verify(otfSpy, times(1)).transform(s, c)
      }

      "transform a server compound op against a client discrete op" in new WithIdnetityTransform {
        val s1 = StringInsertOperation(valueId, false, 1, "s1")
        val s2 = StringInsertOperation(valueId, false, 1, "s2")
        val s = CompoundOperation(List(s1, s2))

        val c = StringInsertOperation(valueId, false, 1, "c")
        transformer.transform(s, c)

        val ordered = Mockito.inOrder(otfSpy)

        ordered.verify(otfSpy, times(1)).transform(s1, c)
        ordered.verify(otfSpy, times(1)).transform(s2, c)
      }

      "transform a client compound op against a server discrete op" in new WithIdnetityTransform {
        val s = StringInsertOperation(valueId, false, 1, "s")

        val c1 = StringInsertOperation(valueId, false, 1, "c1")
        val c2 = StringInsertOperation(valueId, false, 1, "c2")
        val c = CompoundOperation(List(c1, c2))
        transformer.transform(s, c)

        val ordered = Mockito.inOrder(otfSpy)

        ordered.verify(otfSpy, times(1)).transform(s, c1)
        ordered.verify(otfSpy, times(1)).transform(s, c2)
      }

      "transform a server compound op against a server compound  op" in new WithIdnetityTransform {
        val s1 = StringInsertOperation(valueId, false, 1, "s1")
        val s2 = StringInsertOperation(valueId, false, 1, "s2")
        val s = CompoundOperation(List(s1, s2))

        val c1 = StringInsertOperation(valueId, false, 1, "c1")
        val c2 = StringInsertOperation(valueId, false, 1, "c2")
        val c = CompoundOperation(List(c1, c2))
        transformer.transform(s, c)

        val ordered = Mockito.inOrder(otfSpy)

        ordered.verify(otfSpy, times(1)).transform(s1, c1)
        ordered.verify(otfSpy, times(1)).transform(s1, c2)
        ordered.verify(otfSpy, times(1)).transform(s2, c1)
        ordered.verify(otfSpy, times(1)).transform(s2, c2)
      }

      "perform no transformation if the server is a noOp" in new WithIdnetityTransform {
        val s = StringInsertOperation(valueId, true, 1, "s")
        val c = StringInsertOperation(valueId, false, 1, "c")
        val (sx, cx) = transformer.transform(s, c)
        sx shouldBe s
        cx shouldBe c

        verify(otfSpy, times(0)).transform(s, c)
      }

      "perform no transformation if the client is a noOp" in new WithIdnetityTransform {
        val s = StringInsertOperation(valueId, false, 1, "s")
        val c = StringInsertOperation(valueId, true, 1, "c")
        val (sx, cx) = transformer.transform(s, c)
        sx shouldBe s
        cx shouldBe c

        verify(otfSpy, times(0)).transform(s, c)
      }

      "perform no transformation if both ops are noOps" in new WithIdnetityTransform {
        val s = StringInsertOperation(valueId, true, 1, "s")
        val c = StringInsertOperation(valueId, true, 1, "c")
        val (sx, cx) = transformer.transform(s, c)
        sx shouldBe s
        cx shouldBe c

        verify(otfSpy, times(0)).transform(s, c)
      }
    }

    "transforming unrelated operations" must {
      "not transform the operations" in new WithIdnetityTransform {
        val s = StringInsertOperation("x", false, 1, "s")
        val c = StringInsertOperation("y", false, 1, "c")
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
            StringInsertOperation(valueId, false, 1, "s"),
            StringInsertOperation(valueId, false, 1, "s"))
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

  trait WithIdnetityTransform extends TestFixture {
    when(tfRegistry.getOperationTransformationFunction(anyObject[DiscreteOperation](), anyObject[DiscreteOperation]())).thenReturn(Some(otfSpy))
  }

  class IdentityTransform extends OperationTransformationFunction[DiscreteOperation, DiscreteOperation] {
    def transform(s: DiscreteOperation, c: DiscreteOperation): (DiscreteOperation, DiscreteOperation) = (s, c)
  }
}
