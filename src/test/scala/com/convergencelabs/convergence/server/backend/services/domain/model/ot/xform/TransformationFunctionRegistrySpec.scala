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

import java.time.Instant

import com.convergencelabs.convergence.server.backend.services.domain.model.data.StringValue
import com.convergencelabs.convergence.server.backend.services.domain.model.ot._
import com.convergencelabs.convergence.server.backend.services.domain.model.ot.xform.array._
import com.convergencelabs.convergence.server.backend.services.domain.model.ot.xform.bool._
import com.convergencelabs.convergence.server.backend.services.domain.model.ot.xform.date._
import com.convergencelabs.convergence.server.backend.services.domain.model.ot.xform.number._
import com.convergencelabs.convergence.server.backend.services.domain.model.ot.xform.obj._
import com.convergencelabs.convergence.server.backend.services.domain.model.ot.xform.reference._
import com.convergencelabs.convergence.server.backend.services.domain.model.ot.xform.string._
import com.convergencelabs.convergence.server.backend.services.domain.model.{ElementReferenceValues, IndexReferenceValues, PropertyReferenceValues, RangeReferenceValues}
import org.scalatest.OptionValues.convertOptionToValuable
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

// scalastyle:off multiple.string.literals
class TransformationFunctionRegistrySpec extends AnyWordSpec with Matchers {

  private[this] val valueId = "testId"

  private[this] val StringInsert = StringInsertOperation(valueId, noOp = false, 1, "")
  private[this] val StringRemove = StringRemoveOperation(valueId, noOp = false, 1, "")
  private[this] val StringSet = StringSetOperation(valueId, noOp = false, "4")

  private[this] val ArrayInsert = ArrayInsertOperation(valueId, noOp = false, 1, StringValue("id", "4"))
  private[this] val ArrayRemove = ArrayRemoveOperation(valueId, noOp = false, 1)
  private[this] val ArrayReplace = ArrayReplaceOperation(valueId, noOp = false, 1, StringValue("id", "4"))
  private[this] val ArrayMove = ArrayMoveOperation(valueId, noOp = false, 1, 1)
  private[this] val ArraySet = ArraySetOperation(valueId, noOp = false, List(StringValue("id", "4")))

  private[this] val ObjectAddProperty = ObjectAddPropertyOperation(valueId, noOp = false, "prop", StringValue("id", "4"))
  private[this] val ObjectSetProperty = ObjectSetPropertyOperation(valueId, noOp = false, "prop", StringValue("id", "4"))
  private[this] val ObjectRemoveProperty = ObjectRemovePropertyOperation(valueId, noOp = false, "prop")
  private[this] val ObjectSet = ObjectSetOperation(valueId, noOp = false, Map())

  private[this] val NumberAdd = NumberAddOperation(valueId, noOp = false, 1d)
  private[this] val NumberSet = NumberSetOperation(valueId, noOp = false, 1d)

  private[this] val BooleanSet = BooleanSetOperation(valueId, noOp = false, value = true)

  private[this] val DateSet = DateSetOperation(valueId, noOp = false, Instant.now())

  private[this] val referenceKey = "refKey"

  private [this] val IndexValues = IndexReferenceValues(List())
  private [this] val RangeValues = RangeReferenceValues(List())
  private [this] val ElementValues = ElementReferenceValues(List())
  private [this] val PropertyValues = PropertyReferenceValues(List())

  "A TransformationFunctionRegistry" when {

    ///////////////////////////////////////////////////////////////////////////
    // String Operations
    ///////////////////////////////////////////////////////////////////////////

    "getting a TransformationFunction for a StringInsertOperation and anoter StringOperation" must {
      "return the StringInsertInsertTF when a StringInsertOperation and a StringInsertOperation are passed in" in {
        val tfr = new TransformationFunctionRegistry()
        val tf = tfr.getOperationTransformationFunction(StringInsert, StringInsert)
        tf.value shouldBe StringInsertInsertTF
      }

      "return the StringInsertInsertTF when a StringInsertOperation and a StringRemoveOperation are passed in" in {
        val tfr = new TransformationFunctionRegistry()
        val tf = tfr.getOperationTransformationFunction(StringInsert, StringRemove)
        tf.value shouldBe StringInsertRemoveTF
      }

      "return the StringInsertInsertTF when a StringInsertOperation and a StringSetOperation are passed in" in {
        val tfr = new TransformationFunctionRegistry()
        val tf = tfr.getOperationTransformationFunction(StringInsert, StringSet)
        tf.value shouldBe StringInsertSetTF
      }
    }

    "getting a TransformationFunction for a StringRemoveOperation and anoter StringOperation" must {
      "return the StringInsertInsertTF when a StringRemoveOperation and a StringInsertOperation are passed in" in {
        val tfr = new TransformationFunctionRegistry()
        val tf = tfr.getOperationTransformationFunction(StringRemove, StringInsert)
        tf.value shouldBe StringRemoveInsertTF
      }

      "return the StringRemoveRemoveTF when a StringRemoveOperation and a StringRemoveOperation are passed in" in {
        val tfr = new TransformationFunctionRegistry()
        val tf = tfr.getOperationTransformationFunction(StringRemove, StringRemove)
        tf.value shouldBe StringRemoveRemoveTF
      }

      "return the StringRemoveRemoveTF when a StringRemoveOperation and a StringSetOperation are passed in" in {
        val tfr = new TransformationFunctionRegistry()
        val tf = tfr.getOperationTransformationFunction(StringRemove, StringSet)
        tf.value shouldBe StringRemoveSetTF
      }
    }

    "getting a TransformationFunction for a StringSetOperation and anoter StringOperation" must {
      "return the StringInsertInsertTF when a StringSetOperation and a StringInsertOperation are passed in" in {
        val tfr = new TransformationFunctionRegistry()
        val tf = tfr.getOperationTransformationFunction(StringSet, StringInsert)
        tf.value shouldBe StringSetInsertTF
      }

      "return the StringSetSetTF when a StringSetOperation and a StringRemoveOperation are passed in" in {
        val tfr = new TransformationFunctionRegistry()
        val tf = tfr.getOperationTransformationFunction(StringSet, StringRemove)
        tf.value shouldBe StringSetRemoveTF
      }

      "return the StringSetSetTF when a StringSetOperation and a StringSetOperation are passed in" in {
        val tfr = new TransformationFunctionRegistry()
        val tf = tfr.getOperationTransformationFunction(StringSet, StringSet)
        tf.value shouldBe StringSetSetTF
      }
    }

    ///////////////////////////////////////////////////////////////////////////
    // Array Operations
    ///////////////////////////////////////////////////////////////////////////

    "getting a TransformationFunction for an ArrayInsertOperation and anoter ArrayOperation" must {
      "return the ArrayInsertInsertTF when an ArrayInsertOperation and an ArrayInsertOpertion are passed in" in {
        val tfr = new TransformationFunctionRegistry()
        val tf = tfr.getOperationTransformationFunction(ArrayInsert, ArrayInsert)
        tf.value shouldBe ArrayInsertInsertTF
      }

      "return the ArrayInsertRemoveTF when an ArrayInsertOperation and an ArrayRemoveOpertion are passed in" in {
        val tfr = new TransformationFunctionRegistry()
        val tf = tfr.getOperationTransformationFunction(ArrayInsert, ArrayRemove)
        tf.value shouldBe ArrayInsertRemoveTF
      }

      "return the ArrayInsertReplaceTF when an ArrayInsertOperation and an ArrayReplaceOpertion are passed in" in {
        val tfr = new TransformationFunctionRegistry()
        val tf = tfr.getOperationTransformationFunction(ArrayInsert, ArrayReplace)
        tf.value shouldBe ArrayInsertReplaceTF
      }

      "return the ArrayInsertMoveTF when an ArrayInsertOperation and an ArrayMoveOpertion are passed in" in {
        val tfr = new TransformationFunctionRegistry()
        val tf = tfr.getOperationTransformationFunction(ArrayInsert, ArrayMove)
        tf.value shouldBe ArrayInsertMoveTF
      }

      "return the ArrayInsertSetTF when an ArrayInsertOperation and an ArraySetOpertion are passed in" in {
        val tfr = new TransformationFunctionRegistry()
        val tf = tfr.getOperationTransformationFunction(ArrayInsert, ArraySet)
        tf.value shouldBe ArrayInsertSetTF
      }
    }

    "getting a TransformationFunction for an ArrayRemoveOperation and anoter ArrayOperation" must {
      "return the ArrayRemoveInsertTF when an ArrayRemoveOperation and an ArrayInsertOpertion are passed in" in {
        val tfr = new TransformationFunctionRegistry()
        val tf = tfr.getOperationTransformationFunction(ArrayRemove, ArrayInsert)
        tf.value shouldBe ArrayRemoveInsertTF
      }

      "return the ArrayRemoveRemoveTF when an ArrayRemoveOperation and an ArrayRemoveOpertion are passed in" in {
        val tfr = new TransformationFunctionRegistry()
        val tf = tfr.getOperationTransformationFunction(ArrayRemove, ArrayRemove)
        tf.value shouldBe ArrayRemoveRemoveTF
      }

      "return the ArrayRemoveReplaceTF when an ArrayRemoveOperation and an ArrayReplaceOpertion are passed in" in {
        val tfr = new TransformationFunctionRegistry()
        val tf = tfr.getOperationTransformationFunction(ArrayRemove, ArrayReplace)
        tf.value shouldBe ArrayRemoveReplaceTF
      }

      "return the ArrayRemoveMoveTF when an ArrayRemoveOperation and an ArrayMoveOpertion are passed in" in {
        val tfr = new TransformationFunctionRegistry()
        val tf = tfr.getOperationTransformationFunction(ArrayRemove, ArrayMove)
        tf.value shouldBe ArrayRemoveMoveTF
      }

      "return the ArrayRemoveSetTF when an ArrayRemoveOperation and an ArraySetOpertion are passed in" in {
        val tfr = new TransformationFunctionRegistry()
        val tf = tfr.getOperationTransformationFunction(ArrayRemove, ArraySet)
        tf.value shouldBe ArrayRemoveSetTF
      }
    }

    "getting a TransformationFunction for an ArrayReplaceOperation and anoter ArrayOperation" must {
      "return the ArrayInsertInsertTF when an ArrayReplaceOperation and an ArrayInsertOpertion are passed in" in {
        val tfr = new TransformationFunctionRegistry()
        val tf = tfr.getOperationTransformationFunction(ArrayReplace, ArrayInsert)
        tf.value shouldBe ArrayReplaceInsertTF
      }

      "return the ArrayReplaceRemoveTF when an ArrayReplaceOperation and an ArrayRemoveOpertion are passed in" in {
        val tfr = new TransformationFunctionRegistry()
        val tf = tfr.getOperationTransformationFunction(ArrayReplace, ArrayRemove)
        tf.value shouldBe ArrayReplaceRemoveTF
      }

      "return the ArrayReplaceReplaceTF when an ArrayReplaceOperation and an ArrayReplaceOpertion are passed in" in {
        val tfr = new TransformationFunctionRegistry()
        val tf = tfr.getOperationTransformationFunction(ArrayReplace, ArrayReplace)
        tf.value shouldBe ArrayReplaceReplaceTF
      }

      "return the ArrayReplaceMoveTF when an ArrayReplaceOperation and an ArrayMoveOpertion are passed in" in {
        val tfr = new TransformationFunctionRegistry()
        val tf = tfr.getOperationTransformationFunction(ArrayReplace, ArrayMove)
        tf.value shouldBe ArrayReplaceMoveTF
      }

      "return the ArrayReplaceSetTF when an ArrayReplaceOperation and an ArraySetOpertion are passed in" in {
        val tfr = new TransformationFunctionRegistry()
        val tf = tfr.getOperationTransformationFunction(ArrayReplace, ArraySet)
        tf.value shouldBe ArrayReplaceSetTF
      }
    }

    "getting a TransformationFunction for an ArrayMoveOperation and anoter ArrayOperation" must {
      "return the ArrayInsertInsertTF when an ArrayMoveOperation and an ArrayInsertOpertion are passed in" in {
        val tfr = new TransformationFunctionRegistry()
        val tf = tfr.getOperationTransformationFunction(ArrayMove, ArrayInsert)
        tf.value shouldBe ArrayMoveInsertTF
      }

      "return the ArrayMoveRemoveTF when an ArrayMoveOperation and an ArrayRemoveOpertion are passed in" in {
        val tfr = new TransformationFunctionRegistry()
        val tf = tfr.getOperationTransformationFunction(ArrayMove, ArrayRemove)
        tf.value shouldBe ArrayMoveRemoveTF
      }

      "return the ArrayMoveReplaceTF when an ArrayMoveOperation and an ArrayReplaceOpertion are passed in" in {
        val tfr = new TransformationFunctionRegistry()
        val tf = tfr.getOperationTransformationFunction(ArrayMove, ArrayReplace)
        tf.value shouldBe ArrayMoveReplaceTF
      }

      "return the ArrayMoveMoveTF when an ArrayMoveOperation and an ArrayMoveOpertion are passed in" in {
        val tfr = new TransformationFunctionRegistry()
        val tf = tfr.getOperationTransformationFunction(ArrayMove, ArrayMove)
        tf.value shouldBe ArrayMoveMoveTF
      }

      "return the ArrayMoveSetTF when an ArrayMoveOperation and an ArraySetOpertion are passed in" in {
        val tfr = new TransformationFunctionRegistry()
        val tf = tfr.getOperationTransformationFunction(ArrayMove, ArraySet)
        tf.value shouldBe ArrayMoveSetTF
      }
    }

    "getting a TransformationFunction for an ArraySetOperation and anoter ArrayOperation" must {
      "return the ArraySetInsertTF when an ArraySetOperation and an ArrayInsertOpertion are passed in" in {
        val tfr = new TransformationFunctionRegistry()
        val tf = tfr.getOperationTransformationFunction(ArraySet, ArrayInsert)
        tf.value shouldBe ArraySetInsertTF
      }

      "return the ArraySetRemoveTF when an ArraySetOperation and an ArrayRemoveOpertion are passed in" in {
        val tfr = new TransformationFunctionRegistry()
        val tf = tfr.getOperationTransformationFunction(ArraySet, ArrayRemove)
        tf.value shouldBe ArraySetRemoveTF
      }

      "return the ArraySetReplaceTF when an ArraySetOperation and an ArrayReplaceOpertion are passed in" in {
        val tfr = new TransformationFunctionRegistry()
        val tf = tfr.getOperationTransformationFunction(ArraySet, ArrayReplace)
        tf.value shouldBe ArraySetReplaceTF
      }

      "return the ArraySetMoveTF when an ArraySetOperation and an ArrayMoveOpertion are passed in" in {
        val tfr = new TransformationFunctionRegistry()
        val tf = tfr.getOperationTransformationFunction(ArraySet, ArrayMove)
        tf.value shouldBe ArraySetMoveTF
      }

      "return the ArraySetSetTF when an ArraySetOperation and an ArraySetOpertion are passed in" in {
        val tfr = new TransformationFunctionRegistry()
        val tf = tfr.getOperationTransformationFunction(ArraySet, ArraySet)
        tf.value shouldBe ArraySetSetTF
      }
    }

    ///////////////////////////////////////////////////////////////////////////
    // Object Operations
    ///////////////////////////////////////////////////////////////////////////

    "getting a TransformationFunction for an ObjectSetPropertyOperation and anoter ObjectOperation" must {
      "return the ObjectSetPropertySetPropertyTF when an ObjectSetPropertyOperation and an ObjectSetPropertyOperation are passed in" in {
        val tfr = new TransformationFunctionRegistry()
        val tf = tfr.getOperationTransformationFunction(ObjectSetProperty, ObjectSetProperty)
        tf.value shouldBe ObjectSetPropertySetPropertyTF
      }

      "return the ObjectSetPropertyAddPropertyTF when an ObjectSetPropertyOperation and an ObjectAddPropertyOperation are passed in" in {
        val tfr = new TransformationFunctionRegistry()
        val tf = tfr.getOperationTransformationFunction(ObjectSetProperty, ObjectAddProperty)
        tf.value shouldBe ObjectSetPropertyAddPropertyTF
      }

      "return the ObjectSetPropertyRemovePropertyTF when an ObjectSetPropertyOperation and an ObjectRemovePropertyOperation are passed in" in {
        val tfr = new TransformationFunctionRegistry()
        val tf = tfr.getOperationTransformationFunction(ObjectSetProperty, ObjectRemoveProperty)
        tf.value shouldBe ObjectSetPropertyRemovePropertyTF
      }

      "return the ObjectSetPropertyRemovePropertyTF when an ObjectSetPropertyOperation and an ObjectSetOperation are passed in" in {
        val tfr = new TransformationFunctionRegistry()
        val tf = tfr.getOperationTransformationFunction(ObjectSetProperty, ObjectSet)
        tf.value shouldBe ObjectSetPropertySetTF
      }
    }

    "getting a TransformationFunction for an ObjectAddPropertyOperation and anoter ObjectOperation" must {
      "return the ObjectAddPropertySetPropertyTF when an ObjectAddPropertyOperation and an ObjectSetPropertyOperation are passed in" in {
        val tfr = new TransformationFunctionRegistry()
        val tf = tfr.getOperationTransformationFunction(ObjectAddProperty, ObjectSetProperty)
        tf.value shouldBe ObjectAddPropertySetPropertyTF
      }

      "return the ObjectAddPropertyAddPropertyTF when an ObjectAddPropertyOperation and an ObjectAddPropertyOperation are passed in" in {
        val tfr = new TransformationFunctionRegistry()
        val tf = tfr.getOperationTransformationFunction(ObjectAddProperty, ObjectAddProperty)
        tf.value shouldBe ObjectAddPropertyAddPropertyTF
      }

      "return the ObjectAddPropertyRemovePropertyTF when an ObjectAddPropertyOperation and an ObjectRemovePropertyOperation are passed in" in {
        val tfr = new TransformationFunctionRegistry()
        val tf = tfr.getOperationTransformationFunction(ObjectAddProperty, ObjectRemoveProperty)
        tf.value shouldBe ObjectAddPropertyRemovePropertyTF
      }

      "return the ObjectAddPropertyRemovePropertyTF when an ObjectAddPropertyOperation and an ObjectSetOperation are passed in" in {
        val tfr = new TransformationFunctionRegistry()
        val tf = tfr.getOperationTransformationFunction(ObjectAddProperty, ObjectSet)
        tf.value shouldBe ObjectAddPropertySetTF
      }
    }

    "getting a TransformationFunction for an ObjectRemovePropertyOperation and anoter ObjectOperation" must {
      "return the ObjectRemovePropertySetPropertyTF when an ObjectRemovePropertyOperation and an ObjectSetPropertyOperation are passed in" in {
        val tfr = new TransformationFunctionRegistry()
        val tf = tfr.getOperationTransformationFunction(ObjectRemoveProperty, ObjectSetProperty)
        tf.value shouldBe ObjectRemovePropertySetPropertyTF
      }

      "return the ObjectRemovePropertyAddPropertyTF when an ObjectRemovePropertyOperation and an ObjectAddPropertyOperation are passed in" in {
        val tfr = new TransformationFunctionRegistry()
        val tf = tfr.getOperationTransformationFunction(ObjectRemoveProperty, ObjectAddProperty)
        tf.value shouldBe ObjectRemovePropertyAddPropertyTF
      }

      "return the ObjectRemovePropertyRemovePropertyTF when an ObjectRemovePropertyOperation and an ObjectRemovePropertyOperation are passed in" in {
        val tfr = new TransformationFunctionRegistry()
        val tf = tfr.getOperationTransformationFunction(ObjectRemoveProperty, ObjectRemoveProperty)
        tf.value shouldBe ObjectRemovePropertyRemovePropertyTF
      }

      "return the ObjectRemovePropertyRemovePropertyTF when an ObjectRemovePropertyOperation and an ObjectSetOperation are passed in" in {
        val tfr = new TransformationFunctionRegistry()
        val tf = tfr.getOperationTransformationFunction(ObjectRemoveProperty, ObjectSet)
        tf.value shouldBe ObjectRemovePropertySetTF
      }
    }

    "getting a TransformationFunction for an ObjectSetOperation and anoter ObjectOperation" must {
      "return the ObjectSetSetPropertyTF when an ObjectSetOperation and an ObjectSetPropertyOperation are passed in" in {
        val tfr = new TransformationFunctionRegistry()
        val tf = tfr.getOperationTransformationFunction(ObjectSet, ObjectSetProperty)
        tf.value shouldBe ObjectSetSetPropertyTF
      }

      "return the ObjectSetSetTF when an ObjectSetOperation and an ObjectAddPropertyOperation are passed in" in {
        val tfr = new TransformationFunctionRegistry()
        val tf = tfr.getOperationTransformationFunction(ObjectSet, ObjectAddProperty)
        tf.value shouldBe ObjectSetAddPropertyTF
      }

      "return the ObjectSetRemovePropertyTF when an ObjectSetOperation and an ObjectRemovePropertyOperation are passed in" in {
        val tfr = new TransformationFunctionRegistry()
        val tf = tfr.getOperationTransformationFunction(ObjectSet, ObjectRemoveProperty)
        tf.value shouldBe ObjectSetRemovePropertyTF
      }

      "return the ObjectSetRemovePropertyTF when an ObjectSetOperation and an ObjectSetOperation are passed in" in {
        val tfr = new TransformationFunctionRegistry()
        val tf = tfr.getOperationTransformationFunction(ObjectSet, ObjectSet)
        tf.value shouldBe ObjectSetSetTF
      }
    }

    ///////////////////////////////////////////////////////////////////////////
    // Number Operations
    ///////////////////////////////////////////////////////////////////////////

    "getting a TransformationFunction for an NumberAddOperation and anoter NumberOperation" must {
      "return the NumberAddAddTF when a NumberAddOperation and a NumberAddOperation are passed in" in {
        val tfr = new TransformationFunctionRegistry()
        val tf = tfr.getOperationTransformationFunction(NumberAdd, NumberAdd)
        tf.value shouldBe NumberAddAddTF
      }

      "return the NumberAddSetTF when a NumberAddOperation and a NumberSetOperation are passed in" in {
        val tfr = new TransformationFunctionRegistry()
        val tf = tfr.getOperationTransformationFunction(NumberAdd, NumberSet)
        tf.value shouldBe NumberAddSetTF
      }
    }

    "getting a TransformationFunction for an NumberSetOperation and anoter NumberOperation" must {
      "return the NumberSetAddTF when a NumberSetOperation and a NumberAddOperation are passed in" in {
        val tfr = new TransformationFunctionRegistry()
        val tf = tfr.getOperationTransformationFunction(NumberSet, NumberAdd)
        tf.value shouldBe NumberSetAddTF
      }

      "return the NumberSetSetTF when a NumberSetOperation and a NumberSetOperation are passed in" in {
        val tfr = new TransformationFunctionRegistry()
        val tf = tfr.getOperationTransformationFunction(NumberSet, NumberSet)
        tf.value shouldBe NumberSetSetTF
      }
    }

    ///////////////////////////////////////////////////////////////////////////
    // Boolean Operations
    ///////////////////////////////////////////////////////////////////////////

    "getting a TransformationFunction for an BooleanSetOperation and anoter BooleanOperation" must {
      "return the BooleanSetSetTF when a BooleanSetOperation and a BooleanSetOperation are passed in" in {
        val tfr = new TransformationFunctionRegistry()
        val tf = tfr.getOperationTransformationFunction(BooleanSet, BooleanSet)
        tf.value shouldBe BooleanSetSetTF
      }
    }

    ///////////////////////////////////////////////////////////////////////////
    // Date Operations
    ///////////////////////////////////////////////////////////////////////////

    "getting a TransformationFunction for an DateSetOperation and anoter DateOperation" must {
      "return the DateSetSetTF when a DateSetOperation and a DateSetOperation are passed in" in {
        val tfr = new TransformationFunctionRegistry()
        val tf = tfr.getOperationTransformationFunction(DateSet, DateSet)
        tf.value shouldBe DateSetSetTF
      }
    }

    ///////////////////////////////////////////////////////////////////////////
    // String References
    ///////////////////////////////////////////////////////////////////////////
    "getting a ReferenceTransformationFunction for an StringInsert and an Index reference" must {
      "return StringInsertIndexFT" in {
        val tfr = new TransformationFunctionRegistry()
        val tf = tfr.getReferenceTransformationFunction(StringInsert, IndexValues)
        tf.value shouldBe StringInsertIndexTF
      }
    }

    "getting a ReferenceTransformationFunction for an StringRemove and an Index reference" must {
      "return StringRemoveIndexTF" in {
        val tfr = new TransformationFunctionRegistry()
        val tf = tfr.getReferenceTransformationFunction(StringRemove, IndexValues)
        tf.value shouldBe StringRemoveIndexTF
      }
    }

    "getting a ReferenceTransformationFunction for an StringSet and an Index reference" must {
      "return StringSetIndexTF" in {
        val tfr = new TransformationFunctionRegistry()
        val tf = tfr.getReferenceTransformationFunction(StringSet, IndexValues)
        tf.value shouldBe StringSetIndexTF
      }
    }

    "getting a ReferenceTransformationFunction for an StringInsert and an Range reference" must {
      "return StringInsertIndexFT" in {
        val tfr = new TransformationFunctionRegistry()
        val tf = tfr.getReferenceTransformationFunction(StringInsert, RangeValues)
        tf.value shouldBe StringInsertRangeTF
      }
    }

    "getting a ReferenceTransformationFunction for an StringRemove and an Range reference" must {
      "return StringRemoveRangeTF" in {
        val tfr = new TransformationFunctionRegistry()
        val tf = tfr.getReferenceTransformationFunction(StringRemove, RangeValues)
        tf.value shouldBe StringRemoveRangeTF
      }
    }

    "getting a ReferenceTransformationFunction for an StringSet and an Range reference" must {
      "return StringSetRangeTF" in {
        val tfr = new TransformationFunctionRegistry()
        val tf = tfr.getReferenceTransformationFunction(StringSet, RangeValues)
        tf.value shouldBe StringSetRangeTF
      }
    }


    ///////////////////////////////////////////////////////////////////////////
    // Exceptional Cases
    ///////////////////////////////////////////////////////////////////////////
    "getting a TransformationFunction for an invalid pair of operations" must {
      "return None when a StringOperation is transformed with a non StringOperation" in {
        val tfr = new TransformationFunctionRegistry()
        val tf = tfr.getOperationTransformationFunction(StringSet, NumberAdd)
        tf shouldBe None
      }

      "return None when a ArrayOperation is transformed with a non ArrayOperation" in {
        val tfr = new TransformationFunctionRegistry()
        val tf = tfr.getOperationTransformationFunction(ArraySet, NumberAdd)
        tf shouldBe None
      }

      "return None when a ObjectOperation is transformed with a non ObjectOperation" in {
        val tfr = new TransformationFunctionRegistry()
        val tf = tfr.getOperationTransformationFunction(ObjectSet, NumberAdd)
        tf shouldBe None
      }

      "return None when a NumberOperation is transformed with a non NumberOperation" in {
        val tfr = new TransformationFunctionRegistry()
        val tf = tfr.getOperationTransformationFunction(NumberSet, StringSet)
        tf shouldBe None
      }
    }
  }

  "A RegistryKey" when {
    "creating a RegistryKey using of" must {
      "create the proper instnace" in {
        RegistryKey.of[StringInsertOperation, StringRemoveOperation] shouldBe
          RegistryKey(classOf[StringInsertOperation], classOf[StringRemoveOperation])
      }
    }
  }

  "A TFMap" when {
    "registering a transfomration function" must {
      "return a registered function" in {
        val tfMap = new OTFMap()
        tfMap.register[StringInsertOperation, StringRemoveOperation](StringInsertRemoveTF)
        tfMap.getOperationTransformationFunction(StringInsert, StringRemove).value shouldBe StringInsertRemoveTF
      }

      "return None for a not registered function" in {
        val tfMap = new OTFMap()
        tfMap.getOperationTransformationFunction(StringInsert, StringRemove) shouldBe None
      }

      "disallow a duplicate registration" in {
        val tfMap = new OTFMap()
        tfMap.register[StringInsertOperation, StringRemoveOperation](StringInsertRemoveTF)
        intercept[IllegalArgumentException] {
          tfMap.register[StringInsertOperation, StringRemoveOperation](StringInsertRemoveTF)
        }
      }
    }
  }
}
